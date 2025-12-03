import com.ledger.business.*;
import com.ledger.domain.*;
import com.ledger.orm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


public class ReimbursementControllerTest {
    private Connection connection;

    private User testUser;
    private Ledger testLedger;
    private BasicAccount testAccount;
    private List<LedgerCategory> testCategories;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private ReimbursementTxLinkDAO reimbursementTxLinkDAO;
    private ReimbursementDAO reimbursementDAO;

    private TransactionController transactionController;
    private LedgerController ledgerController;
    private ReportController reportController;
    private AccountController accountController;
    ReimbursementController reimbursementController;

    @BeforeEach
    public void setUp() {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        accountDAO = new AccountDAO(connection);
        transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);
        reimbursementDAO = new ReimbursementDAO(connection, transactionDAO);
        reimbursementTxLinkDAO = new ReimbursementTxLinkDAO(connection, transactionDAO, reimbursementDAO);
        DebtPaymentDAO debtPaymentDAO = new DebtPaymentDAO(connection, transactionDAO);
        InstallmentDAO installmentDAO = new InstallmentDAO(connection, ledgerCategoryDAO);
        InstallmentPaymentDAO installmentPaymentDAO = new InstallmentPaymentDAO(connection, transactionDAO, installmentDAO);

        UserController userController = new UserController(userDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, reimbursementDAO, reimbursementTxLinkDAO, debtPaymentDAO, installmentPaymentDAO, installmentDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO);
        reportController = new ReportController(transactionDAO, accountDAO, ledgerDAO, budgetDAO, new InstallmentDAO(connection, ledgerCategoryDAO), ledgerCategoryDAO, reimbursementTxLinkDAO);

        reimbursementController = new ReimbursementController(transactionDAO,
                reimbursementDAO, reimbursementTxLinkDAO, ledgerCategoryDAO, accountDAO);

        userController.register("test user", "password123");
        testUser = userController.login("test user", "password123");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

        testCategories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());

        testAccount = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
    }

    private void runSchemaScript() {
        executeSqlFile("src/test/resources/schema.sql");
    }

    private void readResetScript() {
        executeSqlFile("src/test/resources/reset.sql");
    }

    private void readDataScript() {
        executeSqlFile("src/test/resources/data.sql");
    }

    private void executeSqlFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String sql;
            try (Stream<String> lines = Files.lines(path)) {
                sql = lines.collect(Collectors.joining("\n"));
            }

            try (Statement stmt = connection.createStatement()) {
                for (String s : sql.split(";")) {
                    if (!s.trim().isEmpty()) {
                        stmt.execute(s);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute " + filePath, e);
        }
    }

    @Test
    public void testCreateReimbursement(){
        Reimbursement record = reimbursementController.create(BigDecimal.valueOf(300.00), testAccount, testLedger, "Test Reimbursement");
        assertNotNull(record);

        assertEquals(0, record.getAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertFalse(record.isEnded());
        assertEquals(0, record.getRemainingAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(testAccount.getId(), record.getFromAccount().getId());
        assertEquals(testLedger.getId(), record.getLedger().getId());

        Account fromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //1000-300=700
    }

    //test claim: record a full reimbursement claim for an expense transaction
    @Test
    public void testFullReimbursementClaim() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        //Reimbursement reimbursement = reimbursementDAO.getByOriginalTransactionId(expense.getId());
        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, "Full Reimbursement Test");

        // Claim full reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                true, testAccount1, LocalDate.now());
        assertTrue(claimResult);

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertTrue(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.ZERO));

        // Verify that a reimbursement transaction was created
        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(updatedReimbursement);
        assertEquals(1, txLinks.size());

        assertEquals(1,  transactionDAO.getByLedgerId(testLedger.getId()).size()); //reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size()); //reimbursement transaction

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));
    }

    @Test
    public void testPartialReimbursementClaim() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, "Partial Reimbursement Test");

        // Claim partial reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                false, testAccount1, LocalDate.now());
        assertTrue(claimResult);

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertFalse(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(300.00)));

        // Verify that a reimbursement transaction was created
        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(updatedReimbursement);
        assertEquals(1, txLinks.size());

        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size()); // reimbursement transaction

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500+200
    }

    //test partial reimbursement claim but is final claim
    @Test
    public void testPartialFinalReimbursementClaim() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);


        //Reimbursement reimbursement = reimbursementDAO.getByOriginalTransactionId(expense.getId());
        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, "Partial Final Reimbursement Test");

        // Claim partial reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                true, testAccount1, LocalDate.now());
        assertTrue(claimResult);

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertTrue(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(300.00)));

        // Verify that a reimbursement transaction was created
        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(updatedReimbursement);
        assertEquals(1, txLinks.size());

        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size()); // reimbursement transaction

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500+200
    }

    //test over claim reimbursement
    @Test
    public void testOverClaimReimbursement() {

        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, "Over Claim Reimbursement Test");

        // Attempt to claim more than the remaining amount
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(300.00),
                false, testAccount1, null);
        assertTrue(claimResult);

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertTrue(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(-100.00)));


        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(updatedReimbursement);
        assertEquals(2, txLinks.size());

        assertEquals(2,  transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(2, transactionDAO.getByAccountId(testAccount1.getId()).size());

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //500+300
    }

    //over claim with final claim
    //test delete: delete reimbursement record, original transaction and all linked reimbursement transactions are also deleted
    @Test
    public void testDeleteReimbursement() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, "Delete Reimbursement Test");

        reimbursementController.claim(reimbursement, BigDecimal.valueOf(300.00),
                null, testAccount1, LocalDate.now());
        //balance of testAccount1: 800.00=500+300
        assertEquals(2, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(2, reimbursementTxLinkDAO.getTransactionsByReimbursement(reimbursement).size());

        // Delete the reimbursement
        boolean deleteResult = reimbursementController.delete(reimbursement);
        assertTrue(deleteResult);

        // Verify that the reimbursement is deleted
        Reimbursement deletedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNull(deletedReimbursement);

        // Verify that all linked reimbursement transactions are deleted
        assertEquals(0,  transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, reimbursementTxLinkDAO.getTransactionsByReimbursement(reimbursement).size());

        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(reimbursement);
        assertEquals(0, txLinks.size());

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(500.00))); //500+300-200-100

        Account originalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, originalAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //no change
    }

    //test cancel claims
    @Test
    public void testCancelClaims() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        // Create a reimbursable expense transaction
        Expense expense = transactionController.createExpense(testLedger, testAccount,
                food, null, LocalDate.now(), BigDecimal.valueOf(400.00));

        //Reimbursement reimbursement = reimbursementDAO.getByOriginalTransactionId(expense.getId());
        Reimbursement reimbursement = reimbursementDAO.getByLedger(testLedger).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(reimbursement);

        // Claim reimbursement
        reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                false, testAccount1, LocalDate.now());
        reimbursementController.claim(reimbursement, BigDecimal.valueOf(100.00),
                false, testAccount1, LocalDate.now());
        reimbursementController.claim(reimbursement, BigDecimal.valueOf(150.00),
                false, testAccount1, LocalDate.now());


        // Cancel the claims
        boolean cancelResult = reimbursementController.cancelClaims(reimbursement);
        assertTrue(cancelResult);

        // Verify that the reimbursement status is reset
        //Reimbursement updatedReimbursement = reimbursementDAO.getByOriginalTransactionId(expense.getId());
        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertFalse(updatedReimbursement.isEnded());
        //assertEquals(ReimbursableStatus.PENDING, updatedReimbursement.getReimbursementStatus());
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(400.00)));

        // Verify that linked reimbursement transactions are deleted
        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(updatedReimbursement);
        assertEquals(0, txLinks.size());

        List<Transaction> allTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, allTransactions.size()); // Only original expense remains

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(500.00))); // No change

        Account originalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, originalAccount.getBalance().compareTo(BigDecimal.valueOf(600.00))); //1000-400
    }

}

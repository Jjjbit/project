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
    private LedgerCategory food;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private ReimbursementTxLinkDAO reimbursementTxLinkDAO;
    private ReimbursementDAO reimbursementDAO;

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
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        accountDAO = new AccountDAO(connection);
        transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);
        reimbursementDAO = new ReimbursementDAO(connection, ledgerCategoryDAO, accountDAO, transactionDAO);
        reimbursementTxLinkDAO = new ReimbursementTxLinkDAO(connection, transactionDAO, reimbursementDAO);
        DebtPaymentDAO debtPaymentDAO = new DebtPaymentDAO(connection);
        LoanTxLinkDAO loanTxLinkDAO = new LoanTxLinkDAO(connection, transactionDAO);
        BorrowingTxLinkDAO borrowingTxLinkDAO = new BorrowingTxLinkDAO(connection, transactionDAO);
        LendingTxLinkDAO lendingTxLinkDAO = new LendingTxLinkDAO(connection, transactionDAO);

        UserController userController = new UserController(userDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO, loanTxLinkDAO, borrowingTxLinkDAO, lendingTxLinkDAO);
        reimbursementController = new ReimbursementController(transactionDAO,
                reimbursementDAO, reimbursementTxLinkDAO, ledgerCategoryDAO, accountDAO);

        userController.register("test user", "password123");
        testUser = userController.login("test user", "password123");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

        List<LedgerCategory> testCategories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        food= testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);


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
        Reimbursement record = reimbursementController.create(BigDecimal.valueOf(300.00), testAccount, testLedger,
                food);
        assertNotNull(record);

        assertEquals(0, record.getAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertFalse(record.isEnded());
        assertEquals(0, record.getRemainingAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(food.getId(), record.getLedgerCategory().getId());
        assertEquals(testAccount.getId(), record.getFromAccount().getId());
        assertEquals(testLedger.getId(), record.getLedger().getId());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, reimbursementTxLinkDAO.getTransactionsByReimbursement(record).size());

        Account fromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //1000-300=700
    }

    //test claim: record a full reimbursement claim
    @Test
    public void testFullReimbursementClaim() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, food);

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
        assertEquals(2, txLinks.size());
        assertEquals(TransactionType.TRANSFER, txLinks.getFirst().getType());
        assertEquals(TransactionType.TRANSFER, txLinks.getLast().getType());

        assertEquals(2,  transactionDAO.getByLedgerId(testLedger.getId()).size()); //reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size()); //reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size()); //original reimbursement funding transaction

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));
    }

    @Test
    public void testPartialReimbursementClaim() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, food);

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
        assertEquals(2, txLinks.size());
        assertEquals(TransactionType.TRANSFER, txLinks.getFirst().getType());
        assertEquals(TransactionType.TRANSFER, txLinks.getLast().getType());

        assertEquals(2, transactionDAO.getByLedgerId(testLedger.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size()); // original reimbursement funding transaction

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500+200
    }

    //test partial reimbursement claim but is final claim
    @Test
    public void testPartialFinalReimbursementClaim() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, food);

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
        assertEquals(3, txLinks.size());

        assertEquals(3, transactionDAO.getByLedgerId(testLedger.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size()); // reimbursement transaction
        assertEquals(1, transactionDAO.getByCategoryId(food.getId()).size());

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
                testLedger, food);

        // Attempt to claim more than the remaining amount
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(300.00),
                true, testAccount1, null);
        assertTrue(claimResult);

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertTrue(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(-100.00)));


        List<Transaction> txLinks = reimbursementTxLinkDAO.getTransactionsByReimbursement(updatedReimbursement);
        assertEquals(3, txLinks.size());

        assertEquals(3,  transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(2, transactionDAO.getByAccountId(testAccount1.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //500+300
    }

    //over claim with final claim
    //test delete: delete reimbursement record, original transaction and all linked reimbursement transactions are also deleted
    @Test
    public void testDeleteReimbursement1() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, food);

        reimbursementController.claim(reimbursement, BigDecimal.valueOf(300.00),
                null, testAccount1, LocalDate.now());
        //balance of testAccount1: 800.00=500+300
        assertEquals(3, transactionDAO.getByLedgerId(testLedger.getId()).size()); //transfer+income
        assertEquals(3, reimbursementTxLinkDAO.getTransactionsByReimbursement(reimbursement).size());

        // Delete the reimbursement
        boolean deleteResult = reimbursementController.delete(reimbursement);
        assertTrue(deleteResult);

        // Verify that the reimbursement is deleted
        assertNull(reimbursementDAO.getById(reimbursement.getId()));

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

    //final partial claim
    @Test
    public void testDeleteReimbursement2() {
        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, food);

        // Claim partial reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                true, testAccount1, LocalDate.now());
        assertTrue(claimResult);

        // Delete the reimbursement
        boolean deleteResult = reimbursementController.delete(reimbursement);
        assertTrue(deleteResult);

        // Verify that the reimbursement is deleted
        assertNull(reimbursementDAO.getById(reimbursement.getId()));

        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, reimbursementTxLinkDAO.getTransactionsByReimbursement(reimbursement).size());
        assertEquals(0, transactionDAO.getByCategoryId(food.getId()).size());

        Account updatedAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(500.00))); //500+200-200
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());

        Account originalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, originalAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //no change
        assertEquals(0, transactionDAO.getByAccountId(testAccount1.getId()).size());
    }

    //test edit reimbursement details
    //reimbursed amount is 399
    //new amount is less than already reimbursed amount
    @Test
    public void testEditReimbursement1() {
        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(400.00), testAccount,
                testLedger, food);

        reimbursementController.claim(reimbursement, BigDecimal.valueOf(320.00),
                false, null, LocalDate.now());

        // Edit reimbursement details
        boolean updateResult = reimbursementController.editReimbursement(reimbursement, BigDecimal.valueOf(300.00));
        assertTrue(updateResult);

        Account updateTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(1020.00))); //1000-300+320=1020

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertEquals(0, updatedReimbursement.getAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(-20.00)));
        assertTrue(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getOriginalTransaction().getAmount().compareTo(BigDecimal.valueOf(300.00)));
    }

    //reimbursed amount is zero
    //new amount is more than already reimbursed amount
    @Test
    public void testEditReimbursement2(){
        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(400.00), testAccount,
                testLedger, food);

        //new amount is more than already reimbursed amount
        boolean updateResult = reimbursementController.editReimbursement(reimbursement, BigDecimal.valueOf(300.00));
        assertTrue(updateResult);

        Account oldFromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, oldFromAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertEquals(0, updatedReimbursement.getAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertFalse(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getOriginalTransaction().getAmount().compareTo(BigDecimal.valueOf(300.00)));
    }

    //reimbursed amount is 100
    //new amount is more than already reimbursed amount
    @Test
    public void testEditReimbursement3(){
        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(400.00), testAccount,
                testLedger, food);

        reimbursementController.claim(reimbursement, BigDecimal.valueOf(100.00),
                false, null, LocalDate.now());

        boolean updateResult = reimbursementController.editReimbursement(reimbursement, BigDecimal.valueOf(300.00));
        assertTrue(updateResult);

        Account fromAccount = accountDAO.getAccountById(testAccount.getId()); //1000-300+100=800
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        Reimbursement updatedReimbursement = reimbursementDAO.getById(reimbursement.getId());
        assertNotNull(updatedReimbursement);
        assertEquals(0, updatedReimbursement.getAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(0, updatedReimbursement.getRemainingAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertFalse(updatedReimbursement.isEnded());
        assertEquals(0, updatedReimbursement.getOriginalTransaction().getAmount().compareTo(BigDecimal.valueOf(300.00)));
    }

}

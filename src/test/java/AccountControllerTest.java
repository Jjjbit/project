import com.ledger.business.*;
import com.ledger.domain.*;
import com.ledger.orm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


public class AccountControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private UserController userController;
    private AccountController accountController;
    private TransactionController transactionController;
    private LedgerController ledgerController;
    private InstallmentPlanController installmentPlanController;
    private AccountDAO accountDAO;
    private UserDAO userDAO;
    private LedgerDAO ledgerDAO;
    private TransactionDAO transactionDAO;
    private CategoryDAO categoryDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private InstallmentPlanDAO installmentPlanDAO;


    @BeforeEach
    public void setUp() throws SQLException {
        connection=ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        categoryDAO = new CategoryDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        installmentPlanDAO = new InstallmentPlanDAO(connection);

        userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO);
        installmentPlanController = new InstallmentPlanController(installmentPlanDAO, transactionDAO, accountDAO);

        userController.register("testuser", "password123"); // create test user and insert into db
        testUser=userController.login("testuser", "password123"); // login to set current user
        //testUser = userController.getCurrentUser();

        testLedger=ledgerController.createLedger("Test Ledger", testUser);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        readResetScript();
    }

    private void runSchemaScript() {
        try {
            Path path = Paths.get("src/test/resources/schema.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema.sql", e);
        }
    }

    private void readResetScript() throws SQLException {
        try {
            Path path = Paths.get("src/test/resources/reset.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read reset.sql", e);
        }
    }

    @Test
    public void testCreateBasicAccount() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Alice's Savings",
                BigDecimal.valueOf(5000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(savedAccount.getId(), testUser.getAccounts().get(0).getId());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(5000.00)));
    }

    @Test
    public void testCreateCreditAccount() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "Bob's Credit Card",
                null,
                BigDecimal.valueOf(2000.00),
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(1000.00),
                null,
                null);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(2000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testCreateLoanAccount_Success_NoReceivingAccount() throws SQLException {
        LoanAccount account = accountController.createLoanAccount(
                "Car Loan",
                null,
                true,
                testUser,
                60,
                0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(15000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(16372.80)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(-16372.80)));
    }

    @Test
    public void testCreateLoanAccount_Success_WithReceivingAccount() throws SQLException {
        BasicAccount receivingAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        LoanAccount loanAccount = accountController.createLoanAccount(
                "Home Loan",
                null,
                true,
                testUser,
                120,
                0,
                BigDecimal.valueOf(4), //4% annual interest rate
                BigDecimal.valueOf(200000.00), //loan amount
                receivingAccount, //receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);

        Account savedLoanAccount = accountDAO.getAccountById(loanAccount.getId());
        assertNotNull(savedLoanAccount);

        assertEquals(2, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(242988.00)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(-237988.00)));
    }

    @Test
    public void testCreateBorrowing_Success_NullToAccount() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(
                testUser,
                "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                null,
                true,
                true,
                null,
                LocalDate.now()
        );

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(-3000.00)));
    }

    @Test
    public void testCreateBorrowing_Success_WithToAccount() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Cash Wallet",
                BigDecimal.valueOf(500),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        BorrowingAccount borrowingAccount = accountController.createBorrowingAccount(
                testUser,
                "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null,
                true,
                true,
                toAccount,
                LocalDate.now()
        );

        Account savedBorrowingAccount = accountDAO.getAccountById(borrowingAccount.getId());
        assertNotNull(savedBorrowingAccount);

        assertEquals(2, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(2000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1500.00)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, toAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00)));

        BorrowingAccount savedToAccount = (BorrowingAccount) accountDAO.getAccountById(savedBorrowingAccount.getId());
        assertEquals(0, savedToAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(1500.00)));

    }

    @Test
    public void testCreateLending_Success_NullFromAccount() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(
                testUser,
                "Charlie",
                BigDecimal.valueOf(4000.00), //amount lent
                null,
                true,
                true,
                null,
                LocalDate.now()
        );

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(4000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(4000.00)));
        LendingAccount savedLendingAccount = (LendingAccount) accountDAO.getAccountById(savedAccount.getId());
        assertEquals(0, savedLendingAccount.getBalance().compareTo(BigDecimal.valueOf(4000.00)));
    }

    @Test
    public void testCreateLending_Success_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Emergency Fund",
                BigDecimal.valueOf(800),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        LendingAccount lendingAccount = accountController.createLendingAccount(
                testUser,
                "Diana",
                BigDecimal.valueOf(250.00), //amount lent
                null,
                true,
                true,
                fromAccount,
                LocalDate.now()
        );

        Account savedLendingAccount = accountDAO.getAccountById(lendingAccount.getId());
        assertNotNull(savedLendingAccount);

        assertEquals(2, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(800.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(800.00)));
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(550.00)));

        LendingAccount savedToAccount = (LendingAccount) accountDAO.getAccountById(savedLendingAccount.getId());
        assertEquals(0, savedToAccount.getBalance().compareTo(BigDecimal.valueOf(250.00)));
    }

    @Test
    public void testDeleteBasicAccount_NullTransaction() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        accountController.deleteAccount(account, true);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteBasicAccount_DeleteTransaction() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Salary"))
                .findFirst()
                .orElseThrow();
        LedgerCategory transport=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Transport"))
                .findFirst()
                .orElseThrow();
        Transaction tx1 = transactionController.createIncome(
                testLedger,
                account,
                salary,
                "Monthly Salary",
                LocalDate.now(),
                BigDecimal.valueOf(1000)
        );
        Transaction tx2= transactionController.createExpense(
                testLedger,
                account,
                transport,
                "Train Ticket",
                LocalDate.now(),
                BigDecimal.valueOf(5.60)
        );

        boolean result=accountController.deleteAccount(account, true);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteBasicAccount_KeepTransactions() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Test Account",
                BigDecimal.valueOf(1000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);
        LedgerCategory salary = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElseThrow();
        LedgerCategory transport=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Transport"))
                .findFirst()
                .orElseThrow();
        Transaction tx1 = transactionController.createIncome(
                testLedger,
                account,
                salary,
                "Monthly Salary",
                LocalDate.now(),
                BigDecimal.valueOf(1000)
        );
        Transaction tx2= transactionController.createExpense(
                testLedger,
                account,
                transport,
                "Train Ticket",
                LocalDate.now(),
                BigDecimal.valueOf(5.60)
        );
        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertNotNull(transactionDAO.getById(tx1.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteCreditAccount_NoTransaction() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "Test Credit Account",
                null,
                BigDecimal.valueOf(1500.00), //balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null,
                null);

        boolean result= accountController.deleteAccount(account, true);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteCreditAccount_WithTransactions_DeleteTransactions() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "Test Credit Account",
                null,
                BigDecimal.valueOf(1500.00), //balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null,
                null);
        LedgerCategory shopping=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Shopping"))
                .findFirst()
                .orElseThrow();
        Transaction tx1= transactionController.createExpense(
                testLedger,
                account,
                shopping,
                "New Shoes",
                LocalDate.now(),
                BigDecimal.valueOf(120.00)
        );
        Transaction tx2= transactionController.createExpense(
                testLedger,
                account,
                shopping,
                "Jacket",
                LocalDate.now(),
                BigDecimal.valueOf(80.00)
        );

        boolean result= accountController.deleteAccount(account, true);
        assertTrue(result);
        //deleted account and transactions from db
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));

        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteCreditAccount_WithTransactions_KeepTransactions() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "Test Credit Account",
                null,
                BigDecimal.valueOf(1500.00), //balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null,
                null);
        LedgerCategory shopping = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElseThrow();
        Transaction tx1 = transactionController.createExpense(
                testLedger,
                account,
                shopping,
                "New Shoes",
                LocalDate.now(),
                BigDecimal.valueOf(120.00)
        );
        Transaction tx2 = transactionController.createExpense(
                testLedger,
                account,
                shopping,
                "Jacket",
                LocalDate.now(),
                BigDecimal.valueOf(80.00)
        );
        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);
        //deleted account but kept transactions in db
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        //did not delete transactions
        assertNotNull(transactionDAO.getById(tx1.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));
        assertEquals(2, testLedger.getTransactions().size());

        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteCreditAccount_WithInstallmentPlan() throws SQLException{
        LedgerCategory category=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Shopping"))
                .findFirst()
                .orElseThrow();
        CreditAccount account = accountController.createCreditAccount(
                "Test Credit Account",
                null,
                BigDecimal.valueOf(1500.00), //balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null,
                null);

        InstallmentPlan plan=installmentPlanController.createInstallmentPlan(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(1200.00),
                12,
                0,
                BigDecimal.valueOf(2.00), //2% fee rate
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                LocalDate.now(),
                category
        );

        boolean result= accountController.deleteAccount(account, true);
        assertTrue(result);
        //deleted account and installment plan from db
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertNull(installmentPlanDAO.getById(plan.getId()));

        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testDeleteLoanAccount_Success_DeleteTransaction() throws SQLException {
        LoanAccount account = accountController.createLoanAccount(
                "Test Loan Account",
                null,
                true,
                testUser,
                36,
                0,
                BigDecimal.valueOf(5.0), //5% annual interest rate
                BigDecimal.valueOf(10000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);
        Transaction tx=account.getOutgoingTransactions().get(0); //initial loan disbursement transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
        assertNull(transactionDAO.getById(tx.getId()));
    }

    @Test
    public void testDeleteLoanAccount_Success_KeepTransaction() throws SQLException {
        LoanAccount account = accountController.createLoanAccount(
                "Test Loan Account",
                null,
                true,
                testUser,
                36,
                0,
                BigDecimal.valueOf(5.0), //5% annual interest rate
                BigDecimal.valueOf(10000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);
        Transaction tx=account.getOutgoingTransactions().get(0); //initial loan disbursement transaction

        accountController.repayLoan(account, null, testLedger); //make a repayment to have another transaction
        Transaction tx2=account.getIncomingTransactions().get(0); //repayment transaction

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
        assertNotNull(transactionDAO.getById(tx.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));
    }

    @Test
    public void testDeleteBorrowing_Success_DeleteTransaction() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(
                testUser,
                "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null,
                true,
                true,
                null,
                LocalDate.now()
        );
        Transaction tx=account.getOutgoingTransactions().get(0); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
        assertNull(transactionDAO.getById(tx.getId()));
    }

    @Test
    public void testDeleteBorrowing_Success_KeepTransaction() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(
                testUser,
                "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null,
                true,
                true,
                null,
                LocalDate.now()
        );
        Transaction tx=account.getOutgoingTransactions().get(0); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
        assertNotNull(transactionDAO.getById(tx.getId()));
    }

    @Test
    public void testDeleteLending_Success_DeleteTransaction() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(
                testUser,
                "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null,
                true,
                true,
                null,
                LocalDate.now()
        );
        Transaction tx=account.getIncomingTransactions().get(0); //initial lending transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
        assertNull(transactionDAO.getById(tx.getId()));
    }
    @Test
    public void testDeleteLending_Success_KeepTransaction() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(
                testUser,
                "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null,
                true,
                true,
                null,
                LocalDate.now()
        );
        Transaction tx=account.getIncomingTransactions().get(0); //initial lending transaction

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertEquals(0, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.ZERO));
        assertNotNull(transactionDAO.getById(tx.getId()));
    }

    @Test
    public void testPayDebt_Success_NullFromAccount() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "Bob's Credit Card",
                null,
                BigDecimal.valueOf(2000.00),
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(3000.00), //current debt
                null,
                null);

        boolean result= accountController.repayDebt(account, BigDecimal.valueOf(500.00), null, testLedger);
        assertTrue(result);
        Transaction tx=account.getIncomingTransactions().get(0); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));

        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(2500.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(2500.00)));

    }

    @Test
    public void testPayDebt_Success_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(1000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        CreditAccount account = accountController.createCreditAccount(
                "Bob's Credit Card",
                null,
                BigDecimal.valueOf(2000.00),
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(3000.00), //current debt
                null,
                null);

        boolean result= accountController.repayDebt(account, BigDecimal.valueOf(800.00), fromAccount, testLedger);
        assertTrue(result);
        Transaction tx=account.getIncomingTransactions().get(0); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));
        Transaction tx2=fromAccount.getOutgoingTransactions().get(0); //transfer from fromAccount transaction
        assertEquals(tx.getId(), tx2.getId());

        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(2200.00)));
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(2200.00)));

    }

    @Test
    public void testPayLoan_Success_NullFromAccount() throws SQLException {
        LoanAccount account = accountController.createLoanAccount(
                "Personal Loan",
                null,
                true,
                testUser,
                24,
                0,
                BigDecimal.valueOf(4), //6% annual interest rate
                BigDecimal.valueOf(5000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);

        boolean result=accountController.repayLoan(account, null, testLedger);
        assertTrue(result);

        Transaction tx=account.getIncomingTransactions().get(0); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(4993.76))); //first installment
        assertEquals(1, account.getIncomingTransactions().size());
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(4993.76)));

    }

    @Test
    public void testPayLoan_Success_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(2000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        LoanAccount account = accountController.createLoanAccount(
                "Personal Loan",
                null,
                true,
                testUser,
                24,
                0,
                BigDecimal.valueOf(4), //6% annual interest rate
                BigDecimal.valueOf(5000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST);

        boolean result=accountController.repayLoan(account, fromAccount, testLedger);
        assertTrue(result);

        Transaction tx=account.getIncomingTransactions().get(0); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));
        Transaction tx2=fromAccount.getOutgoingTransactions().get(0); //transfer from fromAccount transaction
        assertEquals(tx.getId(), tx2.getId());

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(4993.76))); //first installment
        assertEquals(1, account.getIncomingTransactions().size());
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(1782.88)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(4993.76)));
    }

    @Test
    public void testPayBorrowing_Success_NullFromAccount() throws SQLException {

        BorrowingAccount account = accountController.createBorrowingAccount(
                testUser,
                "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null,
                true,
                true,
                null,
                LocalDate.now()
        );

        boolean result=accountController.payBorrowing(account, BigDecimal.valueOf(300.00), null, testLedger);
        assertTrue(result);

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(1200.00)));
        assertEquals(1, account.getOutgoingTransactions().size()); //initial borrowing + repayment
        assertEquals(1, account.getIncomingTransactions().size());
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1200.00)));
    }

    @Test
    public void testPayBorrowing_Success_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(1000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        BorrowingAccount account = accountController.createBorrowingAccount(
                testUser,
                "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null,
                true,
                true,
                null,
                LocalDate.now()
        );

        boolean result=accountController.payBorrowing(account, BigDecimal.valueOf(400.00), fromAccount, testLedger);
        assertTrue(result);

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(1100.00)));
        assertEquals(1, account.getOutgoingTransactions().size());
        assertEquals(1, account.getIncomingTransactions().size());
        assertEquals(1, fromAccount.getOutgoingTransactions().size());
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(600.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1100.00)));
    }

    @Test
    public void testReceiveLending_Success_NullToAccount() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(
                testUser,
                "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null,
                true,
                true,
                null,
                LocalDate.now()
        );

        boolean result=accountController.receiveLending(account, BigDecimal.valueOf(200.00), null, testLedger);
        assertTrue(result);

        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(800.00)));
        assertEquals(1, account.getIncomingTransactions().size());
        assertEquals(1, account.getOutgoingTransactions().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(800.00)));
    }

    @Test
    public void testReceiveLending_Success_WithToAccount() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(500),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        LendingAccount account = accountController.createLendingAccount(
                testUser,
                "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null,
                true,
                true,
                null,
                LocalDate.now()
        );

        boolean result=accountController.receiveLending(account, BigDecimal.valueOf(300.00), toAccount, testLedger);
        assertTrue(result);

        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(700.00)));
        assertEquals(1, account.getIncomingTransactions().size());
        assertEquals(1, account.getOutgoingTransactions().size());
        assertEquals(1, toAccount.getIncomingTransactions().size());
        assertEquals(0, toAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(1500.00)));
    }


    @Test
    public void testEditBasicAccount_Success() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Old Account Name",
                BigDecimal.valueOf(1200),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                "Initial Notes",
                true,
                true);

        boolean result= accountController.editBasicAccount(
                account,
                "New Account Name",
                BigDecimal.valueOf(1500),
                "Updated Notes",
                false,
                false
        );
        assertTrue(result);

        Account editedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("New Account Name", editedAccount.getName());
        assertEquals(0, editedAccount.getBalance().compareTo(BigDecimal.valueOf(1500)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());

    }

    @Test
    public void testEditCreditAccount_Success() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "My Credit Card",
                "Initial Notes",
                BigDecimal.valueOf(2500.00), //balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(6000.00), //credit limit
                BigDecimal.valueOf(1500.00), //current debt
                null,
                null);

        boolean result = accountController.editCreditAccount(
                account,
                "Updated Credit Card",
                BigDecimal.valueOf(3000.00), //new balance
                "Updated Notes",
                false,
                false,
                BigDecimal.valueOf(7000.00), //new credit limit
                BigDecimal.valueOf(1200.00), //new current debt
                15,
                30
        );
        assertTrue(result);

        Account editedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Credit Card", editedAccount.getName());
        assertEquals(0, editedAccount.getBalance().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertEquals(15, ((CreditAccount) editedAccount).getBillDay());
        assertEquals(30, ((CreditAccount) editedAccount).getDueDay());


        assertEquals(0, ((CreditAccount) editedAccount).getCreditLimit().compareTo(BigDecimal.valueOf(7000.00)));
        assertEquals(0, ((CreditAccount) editedAccount).getCurrentDebt().compareTo(BigDecimal.valueOf(1200.00)));
    }

    @Test
    public void testEditLoanAccount_Success() throws SQLException {
        LoanAccount account = accountController.createLoanAccount(
                "Car Loan",
                "Initial Notes",
                true,
                testUser,
                48,
                0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL);
        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(21429.32)));

        boolean result = accountController.editLoanAccount(
                account,
                "Updated Car Loan",
                "Updated Notes",
                false,
                60, //new total periods
                1, //new repaid periods
                BigDecimal.valueOf(4), //4% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                LocalDate.now().minusMonths(1), //new repayment date
                LoanAccount.RepaymentType.EQUAL_INTEREST
        );
        assertTrue(result);

        LoanAccount editedAccount = (LoanAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Car Loan", editedAccount.getName());
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertEquals(0,  editedAccount.getAnnualInterestRate().compareTo(BigDecimal.valueOf(4)));
        assertEquals(60, editedAccount.getTotalPeriods());
        assertEquals(1, editedAccount.getRepaidPeriods());
        assertEquals(0,  editedAccount.getLoanAmount().compareTo(BigDecimal.valueOf(20000.00)));
        assertEquals(LocalDate.now().minusMonths(1), editedAccount.getRepaymentDay());
        assertEquals(LoanAccount.RepaymentType.EQUAL_INTEREST, editedAccount.getRepaymentType());
        assertEquals(0, editedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(21731.47)));
    }

    @Test
    public void testEditBorrowingAccount_Success() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(
                testUser,
                "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                "Initial Notes",
                true,
                true,
                null,
                LocalDate.now()
        );

        boolean result = accountController.editBorrowingAccount(
                account,
                "Updated Bob",
                BigDecimal.valueOf(2500.00), //new amount borrowed
                "Updated Notes",
                false,
                false,
                true
        );
        assertTrue(result);
        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(2500.00)));

        BorrowingAccount editedAccount = (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Bob", editedAccount.getName());
        assertEquals(0, editedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(2500.00)));
        assertEquals(0, editedAccount.getBorrowingAmount().compareTo(BigDecimal.valueOf(2500.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertTrue(editedAccount.getIsEnded());
    }

    @Test
    public void testEditLendingAccount_Success() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(
                testUser,
                "Charlie",
                BigDecimal.valueOf(4000.00), //amount lent
                "Initial Notes",
                true,
                true,
                null,
                LocalDate.now()
        );

        boolean result = accountController.editLendingAccount(
                account,
                "Updated Charlie",
                BigDecimal.valueOf(3500.00), //new amount lent
                "Updated Notes",
                false,
                false,
                true
        );
        assertTrue(result);
        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(3500.00)));

        LendingAccount editedAccount = (LendingAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Charlie", editedAccount.getName());
        assertEquals(0, editedAccount.getBalance().compareTo(BigDecimal.valueOf(3500.00)));
        assertEquals(0, editedAccount.getLendingAmount().compareTo(BigDecimal.valueOf(3500.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertTrue(editedAccount.getIsEnded());
    }

    @Test
    public void testHideBasicAccount_Success() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);

        boolean result = accountController.hideAccount(account);
        assertTrue(result);

        Account hiddenAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(hiddenAccount);
        assertTrue(hiddenAccount.getHidden());
    }

    @Test
    public void testHideCreditAccount_Success() throws SQLException {
        CreditAccount account = accountController.createCreditAccount(
                "My Credit Card",
                null,
                BigDecimal.valueOf(2500.00), //balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(6000.00), //credit limit
                BigDecimal.valueOf(1500.00), //current debt
                null,
                null);

        boolean result = accountController.hideAccount(account);
        assertTrue(result);

        Account hiddenAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(hiddenAccount);
        assertTrue(hiddenAccount.getHidden());
    }

    @Test
    public void testHideLoanAccount_Success() throws SQLException {
        LoanAccount account = accountController.createLoanAccount(
                "Car Loan",
                null,
                true,
                testUser,
                48,
                0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                null, //no receiving account
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL);

        boolean result = accountController.hideAccount(account);
        assertTrue(result);

        Account hiddenAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(hiddenAccount);
        assertTrue(hiddenAccount.getHidden());
    }


}


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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


public class AccountControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private List<LedgerCategory> testCategories;

    private AccountController accountController;
    private TransactionController transactionController;
    private InstallmentController installmentController;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private InstallmentDAO installmentDAO;


    @BeforeEach
    public void setUp() throws SQLException {
        connection=ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        installmentDAO = new InstallmentDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        installmentController = new InstallmentController(installmentDAO, transactionDAO, accountDAO);

        userController.register("test user", "password123"); // create test user and insert into db
        testUser=userController.login("test user", "password123"); // login to set current user

        testLedger=ledgerController.createLedger("Test Ledger", testUser);

        testCategories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
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
    public void testCreateBasicAccount() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Alice's Savings",
                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser,
                null, true, true);
        assertNotNull(account);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);
        assertEquals(AccountType.CASH, savedAccount.getType());
        assertEquals(AccountCategory.FUNDS, savedAccount.getCategory());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(5000.00)));
        assertTrue(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInNetAsset());
        assertFalse(savedAccount.getHidden());

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());
    }

    @Test
    public void testCreateCreditAccount() throws SQLException {
        CreditAccount account = accountController.createCreditAccount("Bob's Credit Card", null,
                BigDecimal.valueOf(2000.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00), //credit limit
                BigDecimal.valueOf(1000.00), //current debt
                null, null);
        assertNotNull(account);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);
        assertEquals(AccountType.CREDIT_CARD, savedAccount.getType());
        assertEquals(AccountCategory.CREDIT, savedAccount.getCategory());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00)));
        assertEquals(0, ((CreditAccount)savedAccount).getCreditLimit().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(0, ((CreditAccount)savedAccount).getCurrentDebt().compareTo(BigDecimal.valueOf(1000.00)));
        assertTrue(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInNetAsset());
        assertEquals(0, ((CreditAccount) savedAccount).getDueDay());
        assertEquals(0, ((CreditAccount)savedAccount).getBillDay());
        assertFalse(savedAccount.getHidden());

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());
    }

    @Test
    public void testCreateLoanAccount_NoReceivingAccount() throws SQLException {
        LoanAccount account = accountController.createLoanAccount("Car Loan", null, true,
                testUser, 60, 0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(15000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
        assertNotNull(account);
        //remaining amount 16372.80

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);
        assertEquals(AccountType.LOAN, savedAccount.getType());
        assertEquals(AccountCategory.CREDIT, savedAccount.getCategory());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertFalse(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInNetAsset());
        assertFalse(savedAccount.getHidden());
        assertEquals(60, ((LoanAccount)savedAccount).getTotalPeriods());
        assertEquals(0, ((LoanAccount)savedAccount).getRepaidPeriods());
        assertEquals(0, ((LoanAccount)savedAccount).getAnnualInterestRate().compareTo(BigDecimal.valueOf(3.5)));
        assertEquals(0, ((LoanAccount)savedAccount).getLoanAmount().compareTo(BigDecimal.valueOf(15000.00)));
        assertEquals(LoanAccount.RepaymentType.EQUAL_INTEREST, ((LoanAccount)savedAccount).getRepaymentType());
        assertEquals(0, ((LoanAccount)savedAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(16372.80)));

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());

        List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(1, transactions.size()); //initial loan disbursement transaction
        Transaction tx=transactions.getFirst();
        assertEquals(0, tx.getAmount().compareTo(BigDecimal.valueOf(15000.00)));
        assertEquals(TransactionType.TRANSFER, tx.getType());
        assertEquals(account.getId(), tx.getFromAccount().getId());
        assertNull(tx.getToAccount());
        assertEquals("Loan disbursement", tx.getNote());
    }

    @Test
    public void testCreateLoanAccount_WithReceivingAccount() throws SQLException {
        BasicAccount receivingAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        LoanAccount loanAccount = accountController.createLoanAccount("Home Loan", null,
                true, testUser, 120, 0,
                BigDecimal.valueOf(4), //4% annual interest rate
                BigDecimal.valueOf(200000.00), //loan amount
                receivingAccount, LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
        assertNotNull(loanAccount);
        //remaining amount 242988.00

        Account savedLoanAccount = accountDAO.getAccountById(loanAccount.getId());
        assertNotNull(savedLoanAccount);
        assertEquals(AccountType.LOAN, savedLoanAccount.getType());
        assertEquals(AccountCategory.CREDIT, savedLoanAccount.getCategory());
        assertEquals(0, savedLoanAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertFalse(savedLoanAccount.getSelectable());
        assertTrue(savedLoanAccount.getIncludedInNetAsset());
        assertEquals(120, ((LoanAccount)savedLoanAccount).getTotalPeriods());
        assertEquals(0, ((LoanAccount)savedLoanAccount).getRepaidPeriods());
        assertEquals(0, ((LoanAccount)savedLoanAccount).getAnnualInterestRate().compareTo(BigDecimal.valueOf(4)));
        assertEquals(0, ((LoanAccount)savedLoanAccount).getLoanAmount().compareTo(BigDecimal.valueOf(200000.00)));
        assertEquals(LoanAccount.RepaymentType.EQUAL_INTEREST, ((LoanAccount)savedLoanAccount).getRepaymentType());
        assertEquals(0, ((LoanAccount)savedLoanAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(242988.00)));

        Account savedReceivingAccount = accountDAO.getAccountById(receivingAccount.getId());
        assertNotNull(savedReceivingAccount);
        assertEquals(0, savedReceivingAccount.getBalance().compareTo(BigDecimal.valueOf(205000.00))); //5000 + 200000

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(2, userAccounts.size());

        List<Transaction> loanTransactions = transactionDAO.getByAccountId(loanAccount.getId());
        assertEquals(1, loanTransactions.size()); //initial loan disbursement transaction
        Transaction txLoan=loanTransactions.getFirst();
        assertEquals(0, txLoan.getAmount().compareTo(BigDecimal.valueOf(200000.00)));
        assertEquals(TransactionType.TRANSFER, txLoan.getType());
        assertEquals(loanAccount.getId(), txLoan.getFromAccount().getId());
        assertEquals(receivingAccount.getId(), txLoan.getToAccount().getId());

        List<Transaction> receivingTransactions = transactionDAO.getByAccountId(receivingAccount.getId());
        assertEquals(1, receivingTransactions.size()); //initial loan disbursement transaction in receiving account
        Transaction txReceiving=receivingTransactions.getFirst();
        assertEquals(txLoan.getId(), txReceiving.getId()); //same transaction
    }

    @Test
    public void testCreateBorrowing_NoToAccount() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);
        assertNotNull(account);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);
        assertEquals(AccountType.BORROWING, savedAccount.getType());
        assertEquals(AccountCategory.VIRTUAL_ACCOUNT, savedAccount.getCategory());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInNetAsset());
        assertFalse(((BorrowingAccount)savedAccount).getIsEnded());
        assertFalse(savedAccount.getHidden());
        assertEquals(0, ((BorrowingAccount)savedAccount).getBorrowingAmount().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(0, ((BorrowingAccount)savedAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(3000.00)));

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());

        List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(1, transactions.size()); //initial borrowing transaction
        Transaction tx=transactions.getFirst();
        assertEquals(0, tx.getAmount().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(TransactionType.TRANSFER, tx.getType());
        assertEquals(savedAccount.getId(), tx.getFromAccount().getId());
        assertNull(tx.getToAccount());
    }

    @Test
    public void testCreateBorrowing_WithToAccount() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Cash Wallet", BigDecimal.valueOf(500),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);

        BorrowingAccount borrowingAccount = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null, true, true, toAccount, LocalDate.now(), testLedger);

        Account savedBorrowingAccount = accountDAO.getAccountById(borrowingAccount.getId());
        assertNotNull(savedBorrowingAccount);

        Account savedToAccount = accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, savedToAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00))); //500 + 1500

        List<Transaction> borrowingTransactions = transactionDAO.getByAccountId(borrowingAccount.getId());
        assertEquals(1, borrowingTransactions.size()); //initial borrowing transaction
        Transaction txBorrowing=borrowingTransactions.getFirst();
        assertEquals(0, txBorrowing.getAmount().compareTo(BigDecimal.valueOf(1500.00)));

        List<Transaction> toAccountTransactions = transactionDAO.getByAccountId(toAccount.getId());
        assertEquals(1, toAccountTransactions.size()); //initial borrowing transaction in toAccount
        Transaction txToAccount=toAccountTransactions.getFirst();
        assertEquals(txBorrowing.getId(), txToAccount.getId()); //same transaction
        assertEquals(savedToAccount.getId(), txToAccount.getToAccount().getId());

        assertEquals(0, toAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00)));
    }

    @Test
    public void testCreateLending_NoFromAccount() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Charlie",
                BigDecimal.valueOf(4000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);

        assertEquals(AccountType.LENDING, savedAccount.getType());
        assertEquals(AccountCategory.VIRTUAL_ACCOUNT, savedAccount.getCategory());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(4000.00)));
        assertTrue(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInNetAsset());
        assertFalse(((LendingAccount)savedAccount).getIsEnded());
        assertFalse(savedAccount.getHidden());

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());

        List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(1, transactions.size()); //initial lending transaction
        Transaction tx=transactions.getFirst();
        assertEquals(0, tx.getAmount().compareTo(BigDecimal.valueOf(4000.00)));
        assertEquals(TransactionType.TRANSFER, tx.getType());
        assertNull(tx.getFromAccount());
        assertEquals(savedAccount.getId(), tx.getToAccount().getId());
    }

    @Test
    public void testCreateLending_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Emergency Fund",
                BigDecimal.valueOf(800), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        LendingAccount lendingAccount = accountController.createLendingAccount(testUser, "Diana",
                BigDecimal.valueOf(250.00), //amount lent
                null, true, true, fromAccount, LocalDate.now(), testLedger);

        Account savedLendingAccount = accountDAO.getAccountById(lendingAccount.getId());
        assertNotNull(savedLendingAccount);

        Account savedFromAccount = accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, savedFromAccount.getBalance().compareTo(BigDecimal.valueOf(550.00))); //800 - 250

        List<Transaction> lendingTransactions = transactionDAO.getByAccountId(lendingAccount.getId());
        assertEquals(1, lendingTransactions.size()); //initial lending transaction
        Transaction txLending=lendingTransactions.getFirst();
        assertEquals(0, txLending.getAmount().compareTo(BigDecimal.valueOf(250.00)));

        List<Transaction> fromAccountTransactions = transactionDAO.getByAccountId(fromAccount.getId());
        assertEquals(1, fromAccountTransactions.size()); //initial lending transaction in fromAccount
        Transaction txFromAccount=fromAccountTransactions.getFirst();
        assertEquals(txLending.getId(), txFromAccount.getId()); //same transaction
        assertEquals(savedFromAccount.getId(), txFromAccount.getFromAccount().getId());

        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(550.00)));
    }

    @Test
    public void testDeleteBasicAccount_NullTransaction() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);

        boolean deleted = accountController.deleteAccount(account, true);
        assertTrue(deleted);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(0, userAccounts.size());
    }

    @Test
    public void testDeleteBasicAccount_DeleteTransaction() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);

        //find categories for transactions
        LedgerCategory salary=testCategories.stream()
                .filter(cat->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        LedgerCategory transport=testCategories.stream()
                .filter(cat->cat.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        assertNotNull(transport);

        //create transactions linked to the account
        Transaction tx1 = transactionController.createIncome(testLedger, account, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(1000));
        assertNotNull(tx1);
        Transaction tx2= transactionController.createExpense(testLedger, account, transport, "Train Ticket", LocalDate.now(), BigDecimal.valueOf(5.60));
        assertNotNull(tx2);

        boolean result=accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));

        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(0, accountTransactions.size());

        List<Transaction> salaryTransactions = transactionDAO.getByCategoryId(salary.getId());
        assertEquals(0, salaryTransactions.size());
        List<Transaction> transportTransactions = transactionDAO.getByCategoryId(transport.getId());
        assertEquals(0, transportTransactions.size());
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());
    }

    @Test
    public void testDeleteBasicAccount_KeepTransactions() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true,
                true);

        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);
        LedgerCategory transport=testCategories.stream()
                .filter(cat->cat.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        assertNotNull(transport);

        Transaction tx1 = transactionController.createIncome(testLedger, account, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(1000));
        Transaction tx2= transactionController.createExpense(testLedger, account, transport, "Train Ticket", LocalDate.now(), BigDecimal.valueOf(5.60));

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx1.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));

        List<Transaction> salaryTransactions = transactionDAO.getByCategoryId(salary.getId());
        assertEquals(1, salaryTransactions.size());
        List<Transaction> transportTransactions = transactionDAO.getByCategoryId(transport.getId());
        assertEquals(1, transportTransactions.size());
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(0, accountTransactions.size());
    }

    @Test
    public void testDeleteCreditAccount_NoTransaction() throws SQLException {
        CreditAccount account = accountController.createCreditAccount("Test Credit Account", null,
                BigDecimal.valueOf(1500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null, null);

        boolean result= accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        List<Account> accounts=accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(0, accounts.size());
    }

    @Test
    public void testDeleteCreditAccount_DeleteTransactions() throws SQLException {
        CreditAccount account = accountController.createCreditAccount("Test Credit Account", null,
                BigDecimal.valueOf(1500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null, null);

        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);
        LedgerCategory transport=testCategories.stream()
                .filter(cat->cat.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        assertNotNull(transport);

        Transaction tx1 = transactionController.createIncome(testLedger, account, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(1000));
        Transaction tx2= transactionController.createExpense(testLedger, account, transport, "Train Ticket", LocalDate.now(), BigDecimal.valueOf(5.60));

        boolean result= accountController.deleteAccount(account, true);
        assertTrue(result);

        //deleted account and transactions from db
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));

        List<Account> accounts=accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(0, accounts.size());

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());
        List<Transaction> salaryTransactions = transactionDAO.getByCategoryId(salary.getId());
        assertEquals(0, salaryTransactions.size());
        List<Transaction> transportTransactions = transactionDAO.getByCategoryId(transport.getId());
        assertEquals(0, transportTransactions.size());
    }

    @Test
    public void testDeleteCreditAccount_KeepTransactions() throws SQLException {
        CreditAccount account = accountController.createCreditAccount("Test Credit Account", null,
                BigDecimal.valueOf(1500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null, null);
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);
        LedgerCategory transport=testCategories.stream()
                .filter(cat->cat.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        assertNotNull(transport);

        Transaction tx1 = transactionController.createIncome(testLedger, account, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(1000));
        Transaction tx2= transactionController.createExpense(testLedger, account, transport, "Train Ticket", LocalDate.now(), BigDecimal.valueOf(5.60));

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);

        //deleted account but kept transactions in db
        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);
        //did not delete transactions
        assertNotNull(transactionDAO.getById(tx1.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> salaryTransactions = transactionDAO.getByCategoryId(salary.getId());
        assertEquals(1, salaryTransactions.size());
        List<Transaction> transportTransactions = transactionDAO.getByCategoryId(transport.getId());
        assertEquals(1, transportTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(0, accountTransactions.size()); //account is deleted
    }

    @Test
    public void testDeleteCreditAccount_WithInstallmentPlan() throws SQLException{
        LedgerCategory category=testCategories.stream()
                .filter(cat->cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(category);

        CreditAccount account = accountController.createCreditAccount("Test Credit Account", null,
                BigDecimal.valueOf(1500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(3000.00), //credit limit
                BigDecimal.valueOf(500.00), //current debt
                null, null);

        Installment plan= installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(1200.00), 12,
                BigDecimal.valueOf(2.00), //2% interest
                Installment.Strategy.EVENLY_SPLIT, LocalDate.now(), category, true, testLedger);

        boolean result= accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        //deleted account and installment plan from db
        assertNull(installmentDAO.getById(plan.getId()));
        assertEquals(0, installmentDAO.getByAccountId(testUser.getId()).size());
    }

    @Test
    public void testDeleteLoanAccount_Success_DeleteTransaction() throws SQLException {
        LoanAccount account = accountController.createLoanAccount("Test Loan Account", null,
                true, testUser, 36, 0,
                BigDecimal.valueOf(5.0), //5% annual interest rate
                BigDecimal.valueOf(10000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);

        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial loan disbursement transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        //deleted transaction from db
        assertNull(transactionDAO.getById(tx.getId()));
    }

    @Test
    public void testDeleteLoanAccount_Success_KeepTransaction() throws SQLException {
        LoanAccount account = accountController.createLoanAccount("Test Loan Account", null,
                true, testUser, 36, 0,
                BigDecimal.valueOf(5.0), //5% annual interest rate
                BigDecimal.valueOf(10000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);

        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial loan disbursement transaction

        accountController.repayLoan(account, null, testLedger); //make a repayment to have another transaction
        Transaction tx2= transactionDAO.getByAccountId(account.getId()).getLast();

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));
    }

    @Test
    public void testDeleteLoanAccount_ReceivingAccount_KeepTransaction() throws SQLException {
        BasicAccount receivingAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        LoanAccount loanAccount = accountController.createLoanAccount("Home Loan", null,
                true, testUser, 120, 0,
                BigDecimal.valueOf(4), //4% annual interest rate
                BigDecimal.valueOf(200000.00), //loan amount
                receivingAccount, LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);

        Transaction tx= transactionDAO.getByAccountId(loanAccount.getId()).getFirst(); //initial loan disbursement transaction

        boolean result = accountController.deleteAccount(loanAccount, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(loanAccount.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());
        Transaction txInLedger=ledgerTransactions.getFirst();
        assertEquals(tx.getId(), txInLedger.getId());
        List<Transaction> receivingAccountTransactions = transactionDAO.getByAccountId(receivingAccount.getId());
        assertEquals(1, receivingAccountTransactions.size());
        Transaction txInReceiving=receivingAccountTransactions.getFirst();
        assertEquals(tx.getId(), txInReceiving.getId());
    }

    @Test
    public void testDeleteBorrowing_DeleteTransaction() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);

        //Transaction tx=account.getTransactions().getFirst(); //initial borrowing transaction
        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());
    }

    @Test
    public void testDeleteBorrowing_Success_KeepTransaction() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);

        //Transaction tx=account.getTransactions().getFirst(); //initial borrowing transaction
        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());
    }

    @Test
    public void testDeleteLending_Success_DeleteTransaction() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        //Transaction tx=account.getTransactions().getFirst(); //initial lending transaction
        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial lending transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());
    }

    @Test
    public void testDeleteLending_KeepTransaction() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        //Transaction tx=account.getTransactions().getFirst(); //initial lending transaction
        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial lending transaction

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());
    }

    @Test
    public void testPayDebt_NullFromAccount() throws SQLException {
        CreditAccount account = accountController.createCreditAccount("Bob's Credit Card", null,
                BigDecimal.valueOf(2000.00), true, true, testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(3000.00), //current debt
                null, null);

        boolean result= accountController.repayDebt(account, BigDecimal.valueOf(500.00), null, testLedger);
        assertTrue(result);

        Transaction tx= transactionDAO.getByAccountId(account.getId()).getFirst(); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());
        Transaction txInLedger=ledgerTransactions.getFirst();
        assertEquals(tx.getId(), txInLedger.getId());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(1, accountTransactions.size());
        Transaction txInAccount=accountTransactions.getFirst();
        assertEquals(tx.getId(), txInAccount.getId());

        CreditAccount savedAccount= (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(2500.00)));

        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(2500.00)));
    }

    @Test
    public void testPayDebt_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(1000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        CreditAccount account = accountController.createCreditAccount("Bob's Credit Card", null,
                BigDecimal.valueOf(2000.00), true, true, testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(3000.00), //current debt
                null, null);

        boolean result= accountController.repayDebt(account, BigDecimal.valueOf(800.00), fromAccount, testLedger);
        assertTrue(result);

        Transaction tx= transactionDAO.getByAccountId(account.getId()).getFirst(); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());
        Transaction txInLedger=ledgerTransactions.getFirst();
        assertEquals(tx.getId(), txInLedger.getId());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(1, accountTransactions.size());
        Transaction txInAccount=accountTransactions.getFirst();
        assertEquals(tx.getId(), txInAccount.getId());
        List<Transaction> fromAccountTransactions = transactionDAO.getByAccountId(fromAccount.getId());
        assertEquals(1, fromAccountTransactions.size());
        Transaction txInFromAccount=fromAccountTransactions.getFirst();
        assertEquals(tx.getId(), txInFromAccount.getId());

        CreditAccount savedAccount= (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(2200.00)));

        Account updatedFromAccount= accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(200.00)));

        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(2200.00)));
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(200.00)));
    }

    @Test
    public void testPayLoan_NullFromAccount() throws SQLException {
        LoanAccount account = accountController.createLoanAccount("Personal Loan", null,
                true, testUser, 24, 0,
                BigDecimal.valueOf(4), //6% annual interest rate
                BigDecimal.valueOf(5000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);

        boolean result=accountController.repayLoan(account, null, testLedger);
        assertTrue(result);

        //Transaction tx=account.getTransactions().getFirst(); //repayment transaction
        Transaction tx= transactionDAO.getByAccountId(account.getId()).get(1); //repayment transaction
        assertNotNull(transactionDAO.getById(tx.getId()));

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        Transaction txInLedger=ledgerTransactions.get(1);
        assertEquals(tx.getId(), txInLedger.getId());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());
        Transaction txInAccount=accountTransactions.get(1);
        assertEquals(tx.getId(), txInAccount.getId());

        LoanAccount savedAccount= (LoanAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(4993.76)));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(4993.76)));
    }

    @Test
    public void testPayLoan_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(2000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        LoanAccount account = accountController.createLoanAccount("Personal Loan", null,
                true, testUser, 24, 0,
                BigDecimal.valueOf(4), //6% annual interest rate
                BigDecimal.valueOf(5000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);

        boolean result=accountController.repayLoan(account, fromAccount, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());

        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());

        List<Transaction> fromAccountTransactions = transactionDAO.getByAccountId(fromAccount.getId());
        assertEquals(1, fromAccountTransactions.size());

        LoanAccount savedAccount= (LoanAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(4993.76)));

        Account updatedFromAccount= accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1782.88)));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(4993.76))); //first installment
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(1782.88)));
    }

    @Test
    public void testPayBorrowing_NullFromAccount() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);

        boolean result=accountController.payBorrowing(account, BigDecimal.valueOf(300.00), null, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());

        BorrowingAccount savedAccount= (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(1200.00)));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(1200.00)));
    }

    @Test
    public void testPayBorrowing_WithFromAccount() throws SQLException {
        BasicAccount fromAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(1000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger
        );

        boolean result=accountController.payBorrowing(account, BigDecimal.valueOf(400.00), fromAccount, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());
        List<Transaction> fromAccountTransactions = transactionDAO.getByAccountId(fromAccount.getId());
        assertEquals(1, fromAccountTransactions.size());

        BorrowingAccount savedAccount= (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(1100.00)));

        Account updatedFromAccount= accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(600.00)));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(1100.00)));
        assertEquals(0, fromAccount.getBalance().compareTo(BigDecimal.valueOf(600.00)));
    }

    @Test
    public void testReceiveLending_NullToAccount() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        boolean result=accountController.receiveLending(account, BigDecimal.valueOf(200.00), null, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());

        LendingAccount savedAccount= (LendingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(800.00)));
    }

    @Test
    public void testReceiveLending_WithToAccount() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Checking Account",
                BigDecimal.valueOf(500), AccountType.CASH, AccountCategory.FUNDS, testUser,
                null, true, true);

        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        boolean result=accountController.receiveLending(account, BigDecimal.valueOf(300.00), toAccount, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());
        List<Transaction> toAccountTransactions = transactionDAO.getByAccountId(toAccount.getId());
        assertEquals(1, toAccountTransactions.size());

        LendingAccount savedAccount= (LendingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));

        Account updatedToAccount= accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(700.00)));
        assertEquals(0, toAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));
    }

    @Test
    public void testEditBasicAccount_Success() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Old Account Name",
                BigDecimal.valueOf(1200), AccountType.CASH, AccountCategory.FUNDS, testUser,
                "Initial Notes", true, true);

        boolean result= accountController.editBasicAccount(account, "New Account Name",
                BigDecimal.valueOf(1500), "Updated Notes", false, false);
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
        CreditAccount account = accountController.createCreditAccount("My Credit Card",
                "Initial Notes", BigDecimal.valueOf(2500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(6000.00), //credit limit
                BigDecimal.valueOf(1500.00), //current debt
                null, null);

        boolean result = accountController.editCreditAccount(account, "Updated Credit Card",
                BigDecimal.valueOf(3000.00), //new balance
                "Updated Notes", false, false,
                BigDecimal.valueOf(7000.00), //new credit limit
                BigDecimal.valueOf(1200.00), //new current debt
                15, 30);
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
        LoanAccount account = accountController.createLoanAccount("Car Loan", "Initial Notes",
                true, testUser, 48, 0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_PRINCIPAL, testLedger);
        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(21429.16)));

        boolean result = accountController.editLoanAccount(account, "Updated Car Loan",
                "Updated Notes", false,
                60, //new total periods
                1, //new repaid periods
                BigDecimal.valueOf(4), //4% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                LocalDate.now().minusMonths(1), //new repayment date
                LoanAccount.RepaymentType.EQUAL_INTEREST);
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
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                "Initial Notes", true, true, null, LocalDate.now(), testLedger
        );

        boolean result = accountController.editBorrowingAccount(account, "Updated Bob",
                BigDecimal.valueOf(2500.00), //new amount borrowed
                "Updated Notes", false, false, true);
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
        LendingAccount account = accountController.createLendingAccount(testUser, "Charlie",
                BigDecimal.valueOf(4000.00), //amount lent
                "Initial Notes", true, true, null, LocalDate.now(), testLedger);

        boolean result = accountController.editLendingAccount(account, "Updated Charlie",
                BigDecimal.valueOf(3500.00), //new amount lent
                "Updated Notes", false, false, true);
        assertTrue(result);
        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(3500.00)));

        LendingAccount editedAccount = (LendingAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Charlie", editedAccount.getName());
        assertEquals(0, editedAccount.getBalance().compareTo(BigDecimal.valueOf(3500.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertTrue(editedAccount.getIsEnded());
    }

    @Test
    public void testHideBasicAccount_Success() throws SQLException {
        BasicAccount account = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        boolean result = accountController.hideAccount(account);
        assertTrue(result);

        Account hiddenAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(hiddenAccount);
        assertTrue(hiddenAccount.getHidden());
    }

    @Test
    public void testHideCreditAccount_Success() throws SQLException {
        CreditAccount account = accountController.createCreditAccount("My Credit Card", null,
                BigDecimal.valueOf(2500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(6000.00), //credit limit
                BigDecimal.valueOf(1500.00), //current debt
                null, null);

        boolean result = accountController.hideAccount(account);
        assertTrue(result);

        Account hiddenAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(hiddenAccount);
        assertTrue(hiddenAccount.getHidden());
    }

    @Test
    public void testHideLoanAccount_Success() throws SQLException {
        LoanAccount account = accountController.createLoanAccount("Car Loan", null, true,
                testUser, 48, 0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_PRINCIPAL, testLedger);

        boolean result = accountController.hideAccount(account);
        assertTrue(result);

        Account hiddenAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(hiddenAccount);
        assertTrue(hiddenAccount.getHidden());
    }

}
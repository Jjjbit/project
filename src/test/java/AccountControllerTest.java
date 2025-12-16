import com.ledger.business.AccountController;
import com.ledger.business.LedgerController;
import com.ledger.business.TransactionController;
import com.ledger.business.UserController;
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


public class AccountControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private List<LedgerCategory> testCategories;

    private AccountController accountController;
    private TransactionController transactionController;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private DebtPaymentDAO debtPaymentDAO;

    @BeforeEach
    public void setUp() {
        connection=ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);
        debtPaymentDAO = new DebtPaymentDAO(connection, transactionDAO);
        LoanTxLinkDAO loanTxLinkDAO = new LoanTxLinkDAO(connection, transactionDAO);
        BorrowingTxLinkDAO borrowingTxLinkDAO = new BorrowingTxLinkDAO(connection, transactionDAO);
        LendingTxLinkDAO lendingTxLinkDAO = new LendingTxLinkDAO(connection, transactionDAO);

        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO, loanTxLinkDAO, borrowingTxLinkDAO, lendingTxLinkDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, debtPaymentDAO, borrowingTxLinkDAO, loanTxLinkDAO, lendingTxLinkDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO, transactionController);

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
    public void testCreateBasicAccount() {
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

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());
    }

    @Test
    public void testCreateCreditAccount() {
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

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());
    }

    @Test
    public void testCreateLoanAccount_NoReceivingAccount() {
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
    public void testCreateLoanAccount_WithReceivingAccount() {
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
    public void testCreateBorrowing_NoToAccount() {
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
    public void testCreateBorrowing_WithToAccount() {
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
    public void testCreateLending_NoFromAccount() {
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
    public void testCreateLending_WithFromAccount() {
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

//    @Test
//    public void testDeleteBasicAccount_DeleteTransaction() {
//        BasicAccount account = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000),
//                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
//
//        BasicAccount anotherAccount = accountController.createBasicAccount("Another Account", BigDecimal.valueOf(500),
//                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
//
//        //find categories for transactions
//        LedgerCategory salary=testCategories.stream()
//                .filter(cat->cat.getName().equals("Salary"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(salary);
//
//        LedgerCategory transport=testCategories.stream()
//                .filter(cat->cat.getName().equals("Transport"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(transport);
//
//        //create transactions linked to the account
//        Transaction tx1 = transactionController.createIncome(testLedger, account, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(1000));
//        assertNotNull(tx1);
//        Transaction tx2= transactionController.createExpense(testLedger, account, transport, "Train Ticket", LocalDate.now(), BigDecimal.valueOf(5.60));
//        assertNotNull(tx2);
//        Transaction tx3 =transactionController.createTransfer(testLedger, account, anotherAccount, "Self Transfer", LocalDate.now(), BigDecimal.valueOf(100));
//        assertNotNull(tx3);
//        Transaction tx4=transactionController.createTransfer(testLedger, anotherAccount, account, "Self Transfer In", LocalDate.now(), BigDecimal.valueOf(200));
//        assertNotNull(tx4);
//
//        assertTrue(accountController.deleteAccount(account, true));
//
//        Account deletedAccount = accountDAO.getAccountById(account.getId());
//        assertNull(deletedAccount);
//
//        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
//        assertEquals(0, accountTransactions.size());
//
//        List<Transaction> salaryTransactions = transactionDAO.getByCategoryId(salary.getId());
//        assertEquals(0, salaryTransactions.size());
//        List<Transaction> transportTransactions = transactionDAO.getByCategoryId(transport.getId());
//        assertEquals(0, transportTransactions.size());
//        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
//        assertEquals(0, ledgerTransactions.size());
//        assertEquals(0, transactionDAO.getByAccountId(anotherAccount.getId()).size());
//
//        Account anotherSavedAccount = accountDAO.getAccountById(anotherAccount.getId());
//        assertNotNull(anotherSavedAccount);
//        assertEquals(0, anotherSavedAccount.getBalance().compareTo(BigDecimal.valueOf(400.00)));
//    }

    @Test
    public void testDeleteBasicAccount_KeepTransactions() {
        BasicAccount account = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true,
                true);

        BasicAccount anotherAccount = accountController.createBasicAccount("Another Account", BigDecimal.valueOf(500),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);

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
        Transaction tx3 =transactionController.createTransfer(testLedger, account, anotherAccount, "Self Transfer", LocalDate.now(), BigDecimal.valueOf(100));
        assertNotNull(tx3);
        Transaction tx4=transactionController.createTransfer(testLedger, anotherAccount, account, "Self Transfer In", LocalDate.now(), BigDecimal.valueOf(200));
        assertNotNull(tx4);

        assertTrue(accountController.deleteAccount(account));
        assertNull( accountDAO.getAccountById(account.getId()));
        assertNotNull(transactionDAO.getById(tx1.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));
        assertNotNull(transactionDAO.getById(tx3.getId()));
        assertNotNull(transactionDAO.getById(tx4.getId()));
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(transport.getId()).size());
        assertEquals(4, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(2, transactionDAO.getByAccountId(anotherAccount.getId()).size());

        Account anotherSavedAccount = accountDAO.getAccountById(anotherAccount.getId());
        assertEquals(0, anotherSavedAccount.getBalance().compareTo(BigDecimal.valueOf(400.00))); //500+100-200
    }

//    @Test
//    public void testDeleteCreditAccount_DeleteTransactions() {
//        BasicAccount fromAccount = accountController.createBasicAccount("Savings Account",
//                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
//                true, true);
//        CreditAccount account = accountController.createCreditAccount("Test Credit Account", null,
//                BigDecimal.valueOf(1500.00), //balance
//                true, true, testUser, AccountType.CREDIT_CARD,
//                BigDecimal.valueOf(3000.00), //credit limit
//                BigDecimal.valueOf(500.00), //current debt
//                null, null);
//
//        LedgerCategory salary = testCategories.stream()
//                .filter(cat -> cat.getName().equals("Salary"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(salary);
//        LedgerCategory transport=testCategories.stream()
//                .filter(cat->cat.getName().equals("Transport"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(transport);
//
//        Transaction tx1 = transactionController.createIncome(testLedger, account, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(1000));
//        Transaction tx2= transactionController.createExpense(testLedger, account, transport, "Train Ticket", LocalDate.now(), BigDecimal.valueOf(5.60));
//        accountController.repayDebt(account, BigDecimal.valueOf(200.00), fromAccount, testLedger); //create a debt payment transaction
//        Transaction debtPaymentTx = debtPaymentDAO.getTransactionsByAccount(account).getFirst();
//        assertTrue(accountController.deleteAccount(account, true)); //delete account and linked transactions
//        assertNull(accountDAO.getAccountById(account.getId()));
//        assertNull(transactionDAO.getById(tx1.getId()));
//        assertNull(transactionDAO.getById(tx2.getId()));
//        assertNull(transactionDAO.getById(debtPaymentTx.getId()));
//        assertEquals(1, accountDAO.getAccountsByOwnerId(testUser.getId()).size());
//        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
//        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
//        assertEquals(0, transactionDAO.getByCategoryId(transport.getId()).size());
//
//        Account savedFromAccount = accountDAO.getAccountById(fromAccount.getId());
//        assertEquals(0, savedFromAccount.getBalance().compareTo(BigDecimal.valueOf(4800.00)));
//    }

    @Test
    public void testDeleteCreditAccount_KeepTransactions() {
        BasicAccount fromAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);
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
        accountController.repayDebt(account, BigDecimal.valueOf(200.00), fromAccount, testLedger); //pay debt
        Transaction debtPaymentTx = debtPaymentDAO.getTransactionsByAccount(account).getFirst(); //get the debt payment transaction

        assertTrue(accountController.deleteAccount(account));
        assertNull(accountDAO.getAccountById(account.getId()));
        assertNotNull(transactionDAO.getById(tx1.getId()));
        assertNotNull(transactionDAO.getById(tx2.getId()));
        assertNotNull(transactionDAO.getById(debtPaymentTx.getId()));
        assertEquals(1, accountDAO.getAccountsByOwnerId(testUser.getId()).size());
        assertEquals(3, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(transport.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        Account savedFromAccount = accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, savedFromAccount.getBalance().compareTo(BigDecimal.valueOf(4800.00)));

    }

//    @Test
//    public void testDeleteLoanAccount_DeleteTransaction() {
//        BasicAccount receivingAccount = accountController.createBasicAccount("Savings Account",
//                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
//                true, true);
//
//        LoanAccount loanAccount = accountController.createLoanAccount("Home Loan", null,
//                true, testUser, 120, 0,
//                BigDecimal.valueOf(4), //4% annual interest rate
//                BigDecimal.valueOf(200000.00), //loan amount
//                receivingAccount, LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
//
//        Transaction tx= transactionDAO.getByAccountId(loanAccount.getId()).getFirst(); //initial loan disbursement transaction
//
//        assertTrue(accountController.deleteAccount(loanAccount, true)); //delete account and linked transactions
//        assertNull(accountDAO.getAccountById(loanAccount.getId()));
//        assertNull(transactionDAO.getById(tx.getId()));
//        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
//        assertEquals(0, ledgerTransactions.size());
//        List<Transaction> receivingAccountTransactions = transactionDAO.getByAccountId(receivingAccount.getId());
//        assertEquals(0, receivingAccountTransactions.size());
//
//        Account savedReceivingAccount = accountDAO.getAccountById(receivingAccount.getId());
//        assertEquals(0, savedReceivingAccount.getBalance().compareTo(BigDecimal.valueOf(205000.00))); //no change
//    }

    @Test
    public void testDeleteLoanAccount_KeepTransaction() {
        BasicAccount receivingAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(5000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true);

        LoanAccount loanAccount = accountController.createLoanAccount("Home Loan", null,
                true, testUser, 120, 0,
                BigDecimal.valueOf(4), //4% annual interest rate
                BigDecimal.valueOf(200000.00), //loan amount
                receivingAccount, LocalDate.now(), LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);

        Transaction tx= transactionDAO.getByAccountId(loanAccount.getId()).getFirst(); //initial loan disbursement transaction

        assertTrue(accountController.deleteAccount(loanAccount));
        assertNull( accountDAO.getAccountById(loanAccount.getId()));
        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());
        Transaction txInLedger=ledgerTransactions.getFirst();
        assertEquals(tx.getId(), txInLedger.getId());
        List<Transaction> receivingAccountTransactions = transactionDAO.getByAccountId(receivingAccount.getId());
        assertEquals(1, receivingAccountTransactions.size());
        Transaction txInReceiving=receivingAccountTransactions.getFirst();
        assertEquals(tx.getId(), txInReceiving.getId());

        Account savedReceivingAccount = accountDAO.getAccountById(receivingAccount.getId());
        assertEquals(0, savedReceivingAccount.getBalance().compareTo(BigDecimal.valueOf(205000.00))); //5000 + 200000
    }

//    @Test
//    public void testDeleteBorrowing_DeleteTransaction() {
//        BasicAccount receiveAccount = accountController.createBasicAccount("Eve's Account", BigDecimal.valueOf(1000),
//                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
//        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Eve",
//                BigDecimal.valueOf(2000.00), //amount borrowed
//                null, true, true, receiveAccount, LocalDate.now(), testLedger);
//
//        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial borrowing transaction
//
//        assertTrue( accountController.deleteAccount(account, true));
//
//        Account deletedAccount = accountDAO.getAccountById(account.getId());
//        assertNull(deletedAccount);
//
//        assertNull(transactionDAO.getById(tx.getId()));
//        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
//        assertEquals(0, ledgerTransactions.size());
//
//        Account savedReceiveAccount = accountDAO.getAccountById(receiveAccount.getId());
//        assertEquals(0, savedReceiveAccount.getBalance().compareTo(BigDecimal.valueOf(3000.00))); //no change
//    }

    @Test
    public void testDeleteBorrowing_KeepTransaction() {
        BasicAccount receiveAccount = accountController.createBasicAccount("Cash1", BigDecimal.valueOf(1000),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);

        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null, true, true, receiveAccount, LocalDate.now(), testLedger);

        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());

        Account savedReceiveAccount = accountDAO.getAccountById(receiveAccount.getId());
        assertEquals(0, savedReceiveAccount.getBalance().compareTo(BigDecimal.valueOf(3000.00))); //1000 + 2000
    }

//    @Test
//    public void testDeleteLending_DeleteTransaction() {
//        BasicAccount fromAccount = accountController.createBasicAccount("Cash1", BigDecimal.valueOf(1000),
//                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
//        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
//                BigDecimal.valueOf(1000.00), //amount lent
//                null, true, true, fromAccount, LocalDate.now(), testLedger);
//
//        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial lending transaction
//
//        boolean result = accountController.deleteAccount(account, true);
//        assertTrue(result);
//
//        Account deletedAccount = accountDAO.getAccountById(account.getId());
//        assertNull(deletedAccount);
//
//        assertNull(transactionDAO.getById(tx.getId()));
//        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
//        assertEquals(0, ledgerTransactions.size());
//
//        Account savedFromAccount = accountDAO.getAccountById(fromAccount.getId());
//        assertEquals(0, savedFromAccount.getBalance().compareTo(BigDecimal.ZERO));
//    }

    @Test
    public void testDeleteLending_KeepTransaction() {
        BasicAccount fromAccount = accountController.createBasicAccount("Cash1", BigDecimal.valueOf(1000),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, fromAccount, LocalDate.now(), testLedger);

        Transaction tx=transactionDAO.getByAccountId(account.getId()).getFirst(); //initial lending transaction

        boolean result = accountController.deleteAccount(account);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());

        Account savedFromAccount = accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, savedFromAccount.getBalance().compareTo(BigDecimal.ZERO)); //1000 - 1000
    }

    @Test
    public void testPayDebt_WithFromAccount() {
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
    public void testPayLoan_NullFromAccount() {
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
    public void testPayLoan_WithFromAccount() {
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
    public void testPayBorrowing_NullFromAccount() {
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
    public void testPayBorrowing_WithFromAccount() {
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
    public void testReceiveLending_NullToAccount() {
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
    public void testReceiveLending_WithToAccount() {
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
    public void testEditBasicAccount_Success() {
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
    public void testEditCreditAccount_Success() {
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
    public void testEditLoanAccount_Success() {
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
    public void testEditLoanAccount_Boundary() {
        LoanAccount account = accountController.createLoanAccount("Car Loan", "Initial Notes",
                true, testUser, 48, 0,
                BigDecimal.valueOf(3.5), //3.5% annual interest rate
                BigDecimal.valueOf(20000.00), //loan amount
                null, //no receiving account
                LocalDate.now(), LoanAccount.RepaymentType.EQUAL_PRINCIPAL, testLedger);

        boolean result = accountController.editLoanAccount(account, "Updated Short Loan",
                null, false,
                null, //remains same total periods
                48, //new repaid periods
                BigDecimal.valueOf(2.5), //2.5% annual interest rate
                BigDecimal.valueOf(1000.00), //loan amount
                LocalDate.now(), //new repayment date
                LoanAccount.RepaymentType.EQUAL_PRINCIPAL);
        assertTrue(result);

        LoanAccount editedAccount = (LoanAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Short Loan", editedAccount.getName());
        assertNull(editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertEquals(0,  editedAccount.getAnnualInterestRate().compareTo(BigDecimal.valueOf(2.5)));
        assertEquals(48, editedAccount.getTotalPeriods());
        assertEquals(48, editedAccount.getRepaidPeriods());
        assertEquals(0,  editedAccount.getLoanAmount().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(LocalDate.now(), editedAccount.getRepaymentDay());
        assertEquals(LoanAccount.RepaymentType.EQUAL_PRINCIPAL, editedAccount.getRepaymentType());
        assertEquals(0, editedAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(editedAccount.getIsEnded());
    }

    @Test
    public void testEditBorrowingAccount_Success() {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                "Initial Notes", true, true, null, LocalDate.now(), testLedger
        );

        account.setRemainingAmount(BigDecimal.valueOf(500.00));

        boolean result = accountController.editBorrowingAccount(account, "Updated Bob",
                BigDecimal.valueOf(2500.00), //new amount borrowed
                "Updated Notes", false, false, true);
        assertTrue(result);

        BorrowingAccount editedAccount = (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Bob", editedAccount.getName());
        assertEquals(0, editedAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, editedAccount.getBorrowingAmount().compareTo(BigDecimal.valueOf(2500.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertTrue(editedAccount.getIsEnded());
    }

    @Test
    public void testEditLendingAccount_Success() {
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

    //test getVisibleAccount, getSelectableAccounts, getVisibleBorrowingAccounts,
    // getVisibleLendingAccounts, getCreditCardAccounts, getVisibleLoanAccounts
    @Test
    public void testGetAccounts() {
        BasicAccount testAccount = accountController.createBasicAccount("Test Account",
                BigDecimal.valueOf(1000), AccountType.CASH, AccountCategory.FUNDS, testUser, null,
                true, true); //visible

        //create visible CreditAccount
        Account creditAccount1 = accountController.createCreditAccount("Credit Account 1", "Credit account notes",
                BigDecimal.valueOf(500.00), true, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(150.00),
                1, 5);
        assertNotNull(creditAccount1);
        //create CreditAccount not selectable
        Account creditAccount2 = accountController.createCreditAccount("Credit Account 2", "Another credit account notes",
                BigDecimal.valueOf(400.00), true, false, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(1500.00), BigDecimal.valueOf(100.00),
                1, 5);
        assertNotNull(creditAccount2);

        //create visible LoanAccount
        Account loanAccount1 = accountController.createLoanAccount("Loan Account 1", "Loan account notes",
                true, testUser, 36, 0,
                BigDecimal.valueOf(1.00),  BigDecimal.valueOf(5000.00), testAccount, LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
        assertNotNull(loanAccount1);
        //create LoanAccount
        Account loanAccount2 = accountController.createLoanAccount("Loan Account 2", "Another loan account notes",
                true, testUser, 24, 0,
                BigDecimal.valueOf(1.50),  BigDecimal.valueOf(3000.00), testAccount, LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
        assertNotNull(loanAccount2);

        //create visible LendingAccount
        Account lendingAccount1 = accountController.createLendingAccount(testUser, "Lending Account 1",
                BigDecimal.valueOf(100.00), "Mortgage account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount1);
        //create lending account not selectable
        Account lendingAccount2 = accountController.createLendingAccount(testUser, "Lending Account 2",
                BigDecimal.valueOf(200.00), "Friend loan account", true, false,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount2);

        //create visible BorrowingAccount
        Account borrowingAccount1 = accountController.createBorrowingAccount(testUser, "Borrowing Account 1",
                BigDecimal.valueOf(50.00), "Car loan account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(borrowingAccount1);
        //create  borrowing account not selectable
        Account borrowingAccount2 = accountController.createBorrowingAccount(testUser, "Borrowing Account 2",
                BigDecimal.valueOf(30.00), "Personal loan account", true, false,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(borrowingAccount2);

        assertEquals(4, accountController.getSelectableAccounts(testUser).size());
        assertEquals(9, accountController.getAccounts(testUser).size());
        assertEquals(2, accountController.getBorrowingAccounts(testUser).size());
        assertEquals(2, accountController.getLendingAccounts(testUser).size());
        assertEquals(2, accountController.getCreditCardAccounts(testUser).size());
        assertEquals(2, accountController.getLoanAccounts(testUser).size());
    }

}
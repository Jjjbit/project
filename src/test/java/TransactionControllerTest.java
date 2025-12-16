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

public class TransactionControllerTest {
    private Connection connection;

    private User testUser;
    private Ledger testLedger;
    private BasicAccount testAccount;
    private List<LedgerCategory> testCategories;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private LoanTxLinkDAO loanTxLinkDAO;
    private BorrowingTxLinkDAO borrowingTxLinkDAO;
    private LendingTxLinkDAO lendingTxLinkDAO;

    private TransactionController transactionController;
    private LedgerController ledgerController;
    private AccountController accountController;

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
        BudgetDAO budgetDAO = new BudgetDAO(connection);
        DebtPaymentDAO debtPaymentDAO = new DebtPaymentDAO(connection);
        loanTxLinkDAO = new LoanTxLinkDAO(connection, transactionDAO);
        borrowingTxLinkDAO = new BorrowingTxLinkDAO(connection, transactionDAO);
        lendingTxLinkDAO = new LendingTxLinkDAO(connection, transactionDAO);

        UserController userController = new UserController(userDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, debtPaymentDAO, borrowingTxLinkDAO, loanTxLinkDAO, lendingTxLinkDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO, loanTxLinkDAO, borrowingTxLinkDAO, lendingTxLinkDAO);

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

    //test edit transfer of loan
    //test edit transfer of borrowing
    //test edit transfer of lending

    //test edit transfer of payment of debt
    @Test
    public void testEdit_TransferOfDebtPayment(){
        CreditAccount creditCardAccount = accountController.createCreditAccount("Visa Credit Card", null,
                BigDecimal.valueOf(1000.00), true, true, testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(5000.00),
                BigDecimal.valueOf(500.00),
                15, 25);

        accountController.repayDebt(creditCardAccount, BigDecimal.valueOf(100.00), testAccount, testLedger);
        //balance testAccount 1000 - 100 = 900
        //current debt creditCardAccount 500 - 100 = 400

        BasicAccount newToAccount = accountController.createBasicAccount("New Basic Account",
                BigDecimal.valueOf(2000.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser,
                "New Basic Account Notes", true, true);

        BasicAccount newFromAccount = accountController.createBasicAccount("New Basic Account",
                BigDecimal.valueOf(1500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser,
                "New Basic Account Notes", true, true);

        boolean result=transactionController.updateTransfer((Transfer) transactionDAO.getByAccountId(testAccount.getId()).getFirst(),
                newFromAccount, newToAccount, "Updated Debt Payment Transfer",
                LocalDate.now(), BigDecimal.valueOf(600.00),
                testLedger);
        assertFalse(result); //should fail because new amount exceeds current debt

    }

    //test delete transactions relative to loan
    @Test
    public void testDelete_TransactionsOfLoanPayment1(){
        LoanAccount account = accountController.createLoanAccount("Personal Loan", null, true,
                testUser, 36, 0,
                BigDecimal.valueOf(1.00), //initial interest rate
                BigDecimal.valueOf(5000.00), //loan amount
                testAccount, //receive to testAccount. balance 1000 + 10000 = 11000
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST,
                testLedger);
        assertNotNull(account);
        //total amount to repay  141.04*36=5077.44
        //monthly repayment 141.04

        Transaction inizialLoanTransaction = transactionDAO.getByAccountId(account.getId()).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(inizialLoanTransaction);

        accountController.repayLoan(account, testAccount, testLedger); //balance testAccount 5858.96. remaining loan 5077.44-141.04=4936.40
        accountController.repayLoan(account, null, testLedger); //balance testAccount 5858.96. remaining loan 4936.40-141.04=4795.36

        List<Transaction> loanPayments = loanTxLinkDAO.getTransactionByLoan(account);
        assertEquals(3, loanPayments.size());

        boolean deleted1 = transactionController.deleteTransaction(loanPayments.get(1)); //delete first loan payment
        assertTrue(deleted1);
        assertEquals(2, loanTxLinkDAO.getTransactionByLoan(account).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size()); //initial loan + remaining loan payment
        assertEquals(2, transactionDAO.getByAccountId(account.getId()).size());

        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00)));

        Account updatedLoanAccount = accountDAO.getAccountById(account.getId());
        assertEquals(0, ((LoanAccount) updatedLoanAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(4936.40)));
        assertEquals(1, ((LoanAccount)updatedLoanAccount).getRepaidPeriods());
        assertFalse(((LoanAccount)updatedLoanAccount).getIsEnded());

        boolean deleted2 = transactionController.deleteTransaction(loanPayments.get(2)); //delete second loan payment
        assertTrue(deleted2);
        assertEquals(1, loanTxLinkDAO.getTransactionByLoan(account).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size()); //only initial loan transaction left
        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());

        Account finalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00)));

        Account finalLoanAccount = accountDAO.getAccountById(account.getId());
        assertEquals(0, ((LoanAccount) finalLoanAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(5077.44)));
        assertEquals(0, ((LoanAccount)finalLoanAccount).getRepaidPeriods());
        assertFalse(((LoanAccount)finalLoanAccount).getIsEnded());

        boolean deleted3 = transactionController.deleteTransaction(inizialLoanTransaction); //delete initial loan transaction
        assertTrue(deleted3);
        assertEquals(0, loanTxLinkDAO.getTransactionByLoan(account).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        LoanAccount LoanAccount = (LoanAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, LoanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(36, LoanAccount.getRepaidPeriods());
        assertTrue(LoanAccount.getIsEnded());

        Account finalAccount2 = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount2.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //test delete transactions relative to loan - delete in reverse order
    @Test
    public void testDelete_TransactionsOfLoanPayment2(){
        LoanAccount account = accountController.createLoanAccount("Personal Loan", null, true,
                testUser, 36, 0,
                BigDecimal.valueOf(1.00), //initial interest rate
                BigDecimal.valueOf(5000.00), //loan amount
                testAccount, //receive to testAccount. balance 1000 + 10000 = 11000
                LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST,
                testLedger);
        assertNotNull(account);
        //total amount to repay  141.04*36=5077.44
        //monthly repayment 141.04

        Transaction inizialLoanTransaction = transactionDAO.getByAccountId(account.getId()).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(inizialLoanTransaction);

        accountController.repayLoan(account, testAccount, testLedger); //balance testAccount 5858.96. remaining loan 5077.44-141.04=4936.40
        accountController.repayLoan(account, null, testLedger); //balance testAccount 5858.96. remaining loan 4936.40-141.04=4795.36

        List<Transaction> loanPayments = loanTxLinkDAO.getTransactionByLoan(account);
        assertEquals(3, loanPayments.size());

        boolean deleted1 = transactionController.deleteTransaction(inizialLoanTransaction);
        assertTrue(deleted1);
        assertEquals(0, loanTxLinkDAO.getTransactionByLoan(account).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size()); //both loan payments still there
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        LoanAccount LoanAccount = (LoanAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, LoanAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(36, LoanAccount.getRepaidPeriods());
        assertTrue(LoanAccount.getIsEnded());

        Account updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //test delete transactions relative to borrowing
    @Test
    public void testDelete_TransactionsOfBorrowingPayment1() {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(300.00), //borrowed amount
                null, true, true,
                testAccount, //toAccount
                LocalDate.now(), testLedger);
        assertNotNull(account);
        //balance testAccount 1000 + 300 = 1300, remaining borrowing 300

        Transaction inizialBorrowingTransaction = transactionDAO.getByAccountId(account.getId()).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(inizialBorrowingTransaction);

        accountController.payBorrowing(account, BigDecimal.valueOf(100.00), testAccount, testLedger); //balance testAccount 1200, remaining borrowing 200
        accountController.payBorrowing(account, BigDecimal.valueOf(100.00), null, testLedger); //balance testAccount 1200, remaining borrowing 100

        List<Transaction> borrowingPayments = borrowingTxLinkDAO.getTransactionByBorrowing(account);
        assertEquals(3, borrowingPayments.size());

        boolean deleted1 = transactionController.deleteTransaction(borrowingPayments.get(1)); //delete first borrowing payment
        assertTrue(deleted1);
        assertEquals(2, borrowingTxLinkDAO.getTransactionByBorrowing(account).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(2, transactionDAO.getByAccountId(account.getId()).size());

        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1300.00)));

        Account updatedBorrowingAccount = accountDAO.getAccountById(account.getId());
        assertEquals(0, ((BorrowingAccount) updatedBorrowingAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertFalse(((BorrowingAccount)updatedBorrowingAccount).getIsEnded());

        boolean deleted2 = transactionController.deleteTransaction(borrowingPayments.get(2)); //delete second borrowing payment
        assertTrue(deleted2);
        assertEquals(1, borrowingTxLinkDAO.getTransactionByBorrowing(account).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());

        Account finalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount.getBalance().compareTo(BigDecimal.valueOf(1300.00)));

        Account finalBorrowingAccount = accountDAO.getAccountById(account.getId());
        assertEquals(0, ((BorrowingAccount) finalBorrowingAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(300.00)));
        assertFalse(((BorrowingAccount)finalBorrowingAccount).getIsEnded());

        boolean deleted3 = transactionController.deleteTransaction(inizialBorrowingTransaction); //delete initial borrowing transaction
        assertTrue(deleted3);
        assertEquals(0, borrowingTxLinkDAO.getTransactionByBorrowing(account).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        BorrowingAccount finalBorrowingAccount2 = (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, finalBorrowingAccount2.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(finalBorrowingAccount2.getIsEnded());

        Account finalAccount2 = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount2.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //test delete transactions relative to borrowing - delete in reverse order
    @Test
    public void testDelete_TransactionsOfBorrowingPayment2() {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(300.00), //borrowed amount
                null, true, true,
                testAccount, //toAccount
                LocalDate.now(), testLedger);
        assertNotNull(account);
        //balance testAccount 1000 + 300 = 1300, remaining borrowing 300

        Transaction inizialBorrowingTransaction = transactionDAO.getByAccountId(account.getId()).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(inizialBorrowingTransaction);

        accountController.payBorrowing(account, BigDecimal.valueOf(100.00), testAccount, testLedger); //balance testAccount 1200, remaining borrowing 200
        accountController.payBorrowing(account, BigDecimal.valueOf(100.00), null, testLedger); //balance testAccount 1200, remaining borrowing 100

        List<Transaction> borrowingPayments = borrowingTxLinkDAO.getTransactionByBorrowing(account);
        assertEquals(3, borrowingPayments.size());

        boolean deleted1 = transactionController.deleteTransaction(inizialBorrowingTransaction); //delete first borrowing payment
        assertTrue(deleted1);
        assertEquals(0, borrowingTxLinkDAO.getTransactionByBorrowing(account).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        BorrowingAccount finalBorrowingAccount = (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, finalBorrowingAccount.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertTrue(finalBorrowingAccount.getIsEnded());

        Account finalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

    }

    //test delete transactions relative to lending
    @Test
    public void testDelete_TransactionsOfLendingReceivingPayment1() {
        LendingAccount account = accountController.createLendingAccount(testUser, "Bob",
                BigDecimal.valueOf(400.00), //lent amount
                null, true, true,
                testAccount, //fromAccount
                LocalDate.now(), testLedger);
        assertNotNull(account);
        //balance testAccount 1000 - 400 = 600, remaining lending 400

        Transaction inizialLendingTransaction = transactionDAO.getByAccountId(account.getId()).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(inizialLendingTransaction);

        accountController.receiveLending(account, BigDecimal.valueOf(150.00), testAccount, testLedger); //balance testAccount 750, remaining lending 250
        accountController.receiveLending(account, BigDecimal.valueOf(150.00), null, testLedger); //balance testAccount 750, remaining lending 100

        List<Transaction> lendingPayments = lendingTxLinkDAO.getTransactionByLending(account);
        assertEquals(3, lendingPayments.size());

        boolean deleted1 = transactionController.deleteTransaction(lendingPayments.get(1)); //delete first lending payment
        assertTrue(deleted1);
        assertEquals(2, lendingTxLinkDAO.getTransactionByLending(account).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(2, transactionDAO.getByAccountId(account.getId()).size());

        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(600))); //750-150=600

        Account updatedLendingAccount = accountDAO.getAccountById(account.getId());
        assertFalse(((LendingAccount)updatedLendingAccount).getIsEnded());
        assertEquals(0, updatedLendingAccount.getBalance().compareTo(BigDecimal.valueOf(250.00))); //100+150=250

        boolean deleted2 = transactionController.deleteTransaction(lendingPayments.get(2)); //delete second lending payment
        assertTrue(deleted2);
        assertEquals(1, lendingTxLinkDAO.getTransactionByLending(account).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());

        Account finalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount.getBalance().compareTo(BigDecimal.valueOf(600.00)));

        Account finalLendingAccount = accountDAO.getAccountById(account.getId());
        assertFalse(((LendingAccount)finalLendingAccount).getIsEnded());
        assertEquals(0, finalLendingAccount.getBalance().compareTo(BigDecimal.valueOf(400.00))); //250+150=400

        boolean deleted3 = transactionController.deleteTransaction(inizialLendingTransaction); //delete initial lending transaction
        assertTrue(deleted3);
        assertEquals(0, lendingTxLinkDAO.getTransactionByLending(account).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        LendingAccount finalLendingAccount2 = (LendingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, finalLendingAccount2.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(finalLendingAccount2.getIsEnded());

        Account finalAccount2 = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount2.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //test delete transactions relative to lending - delete in reverse order
    @Test
    public void testDelete_TransactionsOfLendingReceivingPayment2() {
        LendingAccount account = accountController.createLendingAccount(testUser, "Bob",
                BigDecimal.valueOf(400.00), //lent amount
                null, true, true,
                testAccount, //fromAccount
                LocalDate.now(), testLedger);
        assertNotNull(account);
        //balance testAccount 1000 - 400 = 600, remaining lending 400

        Transaction inizialLendingTransaction = transactionDAO.getByAccountId(account.getId()).stream()
                .findFirst()
                .orElse(null);
        assertNotNull(inizialLendingTransaction);

        accountController.receiveLending(account, BigDecimal.valueOf(150.00), testAccount, testLedger); //balance testAccount 750, remaining lending 250
        accountController.receiveLending(account, BigDecimal.valueOf(150.00), null, testLedger); //balance testAccount 750, remaining lending 100

        List<Transaction> lendingPayments = lendingTxLinkDAO.getTransactionByLending(account);
        assertEquals(3, lendingPayments.size());

        boolean deleted1 = transactionController.deleteTransaction(inizialLendingTransaction); //delete initial lending transaction
        assertTrue(deleted1);
        assertEquals(0, lendingTxLinkDAO.getTransactionByLending(account).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        LendingAccount finalLendingAccount = (LendingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, finalLendingAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(finalLendingAccount.getIsEnded());

        Account finalAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, finalAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //test delete a payment of debt of a credit card
    @Test
    public void testDeleteDebtPaymentTransaction() {
        CreditAccount creditCardAccount = accountController.createCreditAccount("Visa Credit Card", null,
                BigDecimal.valueOf(1000.00), true, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(5000.00), BigDecimal.valueOf(500.00),
                15, 25);

        accountController.repayDebt(creditCardAccount, BigDecimal.valueOf(50.00), testAccount, testLedger);
        //balance testAccount 1000 - 50 = 950
        //current debt creditCardAccount 500 - 50 = 450
        List<Transaction> debtPayments = transactionDAO.getByAccountId(testAccount.getId()).stream()
                .toList();

        boolean deleted = transactionController.deleteTransaction(debtPayments.getFirst());
        assertTrue(deleted);
        //current debt should be back to 500
        //balance testAccount back to 1000

        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(creditCardAccount.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //verify toAccount balance updated
        CreditAccount updatedToAccount = (CreditAccount) accountDAO.getAccountById(creditCardAccount.getId());
        assertEquals(0, updatedToAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(500.00)));
    }

    //create
    @Test
    public void testCreateIncome_Success() {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Transaction income=transactionController.createIncome(testLedger, testAccount, salary,
                "June Salary",
                LocalDate.of(2024,6,30),
                BigDecimal.valueOf(5000.00));

        assertNotNull(income);
        assertNotNull(transactionDAO.getById(income.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedIncome = transactionDAO.getById(income.getId());
        assertEquals("June Salary", fetchedIncome.getNote());
        assertEquals(0, fetchedIncome.getAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(LocalDate.of(2024,6,30), fetchedIncome.getDate());
        assertEquals(testAccount.getId(), fetchedIncome.getToAccount().getId());
        assertNull(fetchedIncome.getFromAccount());
        assertEquals(salary.getId(), fetchedIncome.getCategory().getId());
        assertEquals(TransactionType.INCOME, fetchedIncome.getType());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00))); //1000 + 5000 = 6000
    }

    @Test
    public void testCreateExpense_Success() {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Transaction expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping",
                LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        assertNotNull(expense);
        assertNotNull(transactionDAO.getById(expense.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(shopping.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedExpense = transactionDAO.getById(expense.getId());
        assertEquals("Grocery Shopping", fetchedExpense.getNote());
        assertEquals(0, fetchedExpense.getAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(LocalDate.of(2024,6,25), fetchedExpense.getDate());
        assertEquals(testAccount.getId(), fetchedExpense.getFromAccount().getId());
        assertNull(fetchedExpense.getToAccount());
        assertEquals(shopping.getId(), fetchedExpense.getCategory().getId());
        assertEquals(TransactionType.EXPENSE, fetchedExpense.getType());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00))); //1000 - 150 = 850
    }

    @Test
    public void testCreateTransfer_Success() {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser,
                "Savings Account Notes", true, true);

        Transaction transfer=transactionController.createTransfer(testLedger, testAccount, toAccount,
                "Transfer to Savings", LocalDate.of(2024,6,20),
                BigDecimal.valueOf(200.00));
        assertNotNull(transfer);
        assertNotNull(transactionDAO.getById(transfer.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(toAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Transfer to Savings", fetchedTransfer.getNote());
        assertEquals(0, fetchedTransfer.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024,6,20), fetchedTransfer.getDate());
        assertEquals(testAccount.getId(), fetchedTransfer.getFromAccount().getId());
        assertEquals(toAccount.getId(), fetchedTransfer.getToAccount().getId());
        assertNull(fetchedTransfer.getCategory());
        assertEquals(TransactionType.TRANSFER, fetchedTransfer.getType());

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //1000 - 200 = 800

        //verify toAccount balance updated
        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500 + 200 = 700
    }

    //delete
    @Test
    public void testDeleteIncome_Success() {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Transaction income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));
        assertNotNull(income);

        boolean deleted=transactionController.deleteTransaction(income);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(income.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteExpense_Success() {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Transaction expense=transactionController.createExpense(testLedger, testAccount, shopping, "Grocery Shopping",
                LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00));
        assertNotNull(expense);

        boolean deleted=transactionController.deleteTransaction(expense);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(expense.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(shopping.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteTransfer_Success() {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account", BigDecimal.valueOf(500.00),
                AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser, "Savings Account Notes",
                true, true);

        Transaction transfer=transactionController.createTransfer(testLedger, testAccount, toAccount,"Transfer to Savings",
                LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00));
        assertNotNull(transfer);

        boolean deleted=transactionController.deleteTransaction(transfer);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(transfer.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(toAccount.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //verify toAccount balance updated
        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(500.00)));
    }

    //edit
    @Test
    public void testEditIncome_Success() {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        Account newAccount = accountController.createBasicAccount("New Account", BigDecimal.valueOf(2000.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "New Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedgerId(newLedger.getId());

        LedgerCategory newCategory = newLedgerCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(newCategory);

        boolean result=transactionController.updateIncome(income, newAccount,
                newCategory, "Updated June Salary", LocalDate.of(2024,6,30),
                BigDecimal.valueOf(6000.00), newLedger);
        assertTrue(result);

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());

        BasicAccount updatedNewAccount = (BasicAccount) accountDAO.getAccountById(newAccount.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(8000.00))); //2000 + 6000 = 8000
        assertEquals(1, transactionDAO.getByAccountId(newAccount.getId()).size());

        assertEquals(1, transactionDAO.getByLedgerId(newLedger.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(newCategory.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertEquals("Updated June Salary", updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(LocalDate.of(2024,6,30), updatedIncome.getDate());
        assertEquals(newAccount.getId(), updatedIncome.getToAccount().getId());
        assertNull(updatedIncome.getFromAccount());
        assertEquals(newCategory.getId(), updatedIncome.getCategory().getId());
        assertEquals(newLedger.getId(), updatedIncome.getLedger().getId());
    }

    @Test
    public void testEditIncome_Invariant_Success() {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        boolean result=transactionController.updateIncome(income, null, null, null,
                null, null, null);
        assertTrue(result);

        //verify account balance unchanged
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());

        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertNull(updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(LocalDate.of(2024,6,30), updatedIncome.getDate());
        assertEquals(testAccount.getId(), updatedIncome.getToAccount().getId());
    }

    @Test
    public void testEditExpense_Success() {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        Account newAccount = accountController.createBasicAccount("New Account", BigDecimal.valueOf(500.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "New Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedgerId(newLedger.getId());

        LedgerCategory newCategory = newLedgerCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(newCategory);

        boolean result=transactionController.updateExpense(expense, newAccount,
                newCategory, "Updated Grocery Shopping", LocalDate.of(2024,6,26),
                BigDecimal.valueOf(200.00), newLedger);
        assertTrue(result);

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(newAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(newLedger.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(newCategory.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(shopping.getId()).size());

        BasicAccount updatedNewAccount = (BasicAccount) accountDAO.getAccountById(newAccount.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(300.00))); //500 - 200 = 300

        Transaction updatedExpense = transactionDAO.getById(expense.getId());
        assertEquals("Updated Grocery Shopping", updatedExpense.getNote());
        assertEquals(0, updatedExpense.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024,6,26), updatedExpense.getDate());
        assertEquals(newAccount.getId(), updatedExpense.getFromAccount().getId());
        assertNull(updatedExpense.getToAccount());
        assertEquals(newCategory.getId(), updatedExpense.getCategory().getId());
        assertEquals(newLedger.getId(), updatedExpense.getLedger().getId());
    }

    @Test
    public void testEditExpense_Invariant() {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        boolean result=transactionController.updateExpense(expense, null,
                null, null, null, null, null);
        assertTrue(result);

        //verify account balance unchanged
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00)));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());

        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(shopping.getId()).size());

        Transaction updatedExpense = transactionDAO.getById(expense.getId());
        assertNull(updatedExpense.getNote());
        assertEquals(0, updatedExpense.getAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(LocalDate.of(2024,6,25), updatedExpense.getDate());
        assertEquals(testAccount.getId(), updatedExpense.getFromAccount().getId());
    }

    @Test
    public void testEditTransfer_Success() {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, "Savings Account Notes", true, true);

        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                toAccount, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);

        boolean result = transactionController.updateTransfer(transfer, toAccount, testAccount,
                "Updated Transfer", LocalDate.of(2024, 6, 21),
                BigDecimal.valueOf(250.00), newLedger);
        assertTrue(result);

        //verify account balances updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1250.00))); //1000-200+200+250=1250

        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(250.00))); //500+200-200-250=250

        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Updated Transfer", updatedTransfer.getNote());
        assertEquals(0, updatedTransfer.getAmount().compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(LocalDate.of(2024, 6, 21), updatedTransfer.getDate());
        assertEquals(toAccount.getId(), updatedTransfer.getFromAccount().getId());
        assertEquals(testAccount.getId(), updatedTransfer.getToAccount().getId());
        assertEquals(newLedger.getId(), updatedTransfer.getLedger().getId());
    }

    //test edit transfer invariant
    //test edit transfer: old note is not null and new note is null
    //test edit transfer: old fromAccount is null and new fromAccount is not null & old toAccount is not null and new toAccount is null
    @Test
    public void testUpdateTransfer_SetNewFromAccount(){
        BasicAccount fromAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, "Savings Account Notes", true, true);
        Transfer transfer = transactionController.createTransfer(testLedger, null,
                testAccount, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        boolean result = transactionController.updateTransfer(transfer, fromAccount, null,
                null, null, null, null);
        assertTrue(result);
        //verify account balances updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(fromAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(300.00))); //500 - 200 = 300

        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //1000-200+200=1000

        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals(fromAccount.getId(), updatedTransfer.getFromAccount().getId());
        assertNull(updatedTransfer.getToAccount());

    }
    //test edit transfer: old fromAccount is not null and new fromAccount is null

    @Test
    public void testGetTransactionsByLedgerInRangeDate_Boundary() {
        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        transactionController.createExpense(testLedger, testAccount, food, "Dinner", startDate, BigDecimal.valueOf(30.00));
        transactionController.createExpense(testLedger, testAccount, food, "Lunch", endDate, BigDecimal.valueOf(20.00));

        List<Transaction> transactions = transactionController.getTransactionsByLedgerInRangeDate(testLedger, startDate, endDate);
        assertEquals(2, transactions.size());

        for (Transaction tx : transactions) {
            StringBuilder info = new StringBuilder();

            info.append(String.format("ID: %d, Type: %s, Amount: %s, Date: %s",
                    tx.getId(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getDate()));

            if (tx.getCategory() != null) {
                info.append(", Category: ").append(tx.getCategory().getName());
            }

            if (tx instanceof Transfer) {
                if (tx.getFromAccount() != null) {
                    info.append(", FROM: ").append(tx.getFromAccount().getName());
                }
                if (tx.getToAccount() != null) {
                    info.append(", TO: ").append(tx.getToAccount().getName());
                }
            } else {
                if (tx.getFromAccount() != null) {
                    info.append(", From: ").append(tx.getFromAccount().getName());
                } else if (tx.getToAccount() != null) {
                    info.append(", To: ").append(tx.getToAccount().getName());
                }
            }

            if (tx.getNote() != null && !tx.getNote().isEmpty()) {
                info.append(", Note: ").append(tx.getNote());
            }

            System.out.println(info);
        }
    }

    @Test
    public void testGetTransactionsByAccountInRangeDate_Boundary() {
        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        LedgerCategory salary = testCategories.stream()
                .filter(c -> c.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        transactionController.createExpense(testLedger, testAccount, food, "Dinner", startDate, BigDecimal.valueOf(30.00));
        transactionController.createExpense(testLedger, testAccount, food, "Lunch", endDate, BigDecimal.valueOf(20.00));
        transactionController.createTransfer(testLedger, testAccount, null, "Transfer to self", startDate, BigDecimal.valueOf(100.00));
        transactionController.createTransfer(testLedger, null, testAccount, "Transfer from self", endDate, BigDecimal.valueOf(200.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", startDate, BigDecimal.valueOf(3000.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", endDate, BigDecimal.valueOf(3000.00));

        List<Transaction> transactions = transactionController.getTransactionsByAccountInRangeDate(testAccount, startDate, endDate);
        assertEquals(6, transactions.size());
        for (Transaction tx : transactions) {
            StringBuilder info = new StringBuilder();

            info.append(String.format("ID: %d, Type: %s, Amount: %s, Date: %s",
                    tx.getId(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getDate()));

            if (tx.getCategory() != null) {
                info.append(", Category: ").append(tx.getCategory().getName());
            }

            if (tx instanceof Transfer) {
                if (tx.getFromAccount() != null) {
                    info.append(", FROM: ").append(tx.getFromAccount().getName());
                }
                if (tx.getToAccount() != null) {
                    info.append(", TO: ").append(tx.getToAccount().getName());
                }
            } else {
                if (tx.getFromAccount() != null) {
                    info.append(", From: ").append(tx.getFromAccount().getName());
                } else if (tx.getToAccount() != null) {
                    info.append(", To: ").append(tx.getToAccount().getName());
                }
            }

            if (tx.getNote() != null && !tx.getNote().isEmpty()) {
                info.append(", Note: ").append(tx.getNote());
            }

            System.out.println(info);
        }
    }

}


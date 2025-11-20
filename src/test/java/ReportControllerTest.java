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
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ReportControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private Account testAccount;
    private List<LedgerCategory> testCategories;

    private BudgetDAO budgetDAO;

    private AccountController accountController;
    private BudgetController budgetController;
    private TransactionController transactionController;
    private ReportController reportController;
    private LedgerController ledgerController;
    private InstallmentController  installmentController;

    @BeforeEach
    public void setUp() throws SQLException{
        connection= ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        budgetDAO = new BudgetDAO(connection);
        TransactionDAO transactionDAO = new TransactionDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        InstallmentDAO installmentDAO = new InstallmentDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);

        budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO, transactionDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO);
        reportController = new ReportController(transactionDAO, accountDAO, ledgerDAO, budgetDAO,
                installmentDAO, ledgerCategoryDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO,
                accountDAO, budgetDAO);
        installmentController = new InstallmentController(installmentDAO, transactionDAO, accountDAO);

        userController.register("test user", "password123");
        testUser = userController.login("test user", "password123");
        testLedger = ledgerController.createLedger("Test Ledger", testUser);
        testCategories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        testAccount = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000.00),
                AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser, "Test account notes",
                true, true);
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

    //test getTransactionsByLedgerInRangeDate
    @Test
    public void testGetTransactionsByLedgerInRangeDate() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        LedgerCategory salary = testCategories.stream()
                .filter(c -> c.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        //transaction outside range
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.of(2024, 6, 1), BigDecimal.valueOf(50.00));
        //transactions within range
        transactionController.createExpense(testLedger, testAccount, food, "Dinner", LocalDate.now(), BigDecimal.valueOf(30.00));
        transactionController.createExpense(testLedger, testAccount, food, "Lunch", LocalDate.now(), BigDecimal.valueOf(20.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(3000.00));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        List<Transaction> transactions = reportController.getTransactionsByLedgerInRangeDate(testLedger, startDate, endDate);
        assertEquals(3, transactions.size());
        for (Transaction tx : transactions) {
            String info = "Transaction ID: " + tx.getId()
                    + ", Date: " + tx.getDate()
                    + ", Amount: " + tx.getAmount()
                    + ", Type: " + tx.getType();

            if(tx.getCategory() != null) {
                info += ", Category: " + tx.getCategory().getName();
            }
            if (tx.getToAccount() != null) {
                info += ", To Account: " + tx.getToAccount().getName();
            } else if (tx.getFromAccount() != null) {
                info += ", From Account: " + tx.getFromAccount().getName();
            }
            if(tx.getNote() != null) {
                info += ", Note: " + tx.getNote();
            }

            System.out.println(info);
        }
    }

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

        List<Transaction> transactions = reportController.getTransactionsByLedgerInRangeDate(testLedger, startDate, endDate);
        assertEquals(2, transactions.size());

        for (Transaction tx : transactions) {
            String info = "Transaction ID: " + tx.getId()
                    + ", Date: " + tx.getDate()
                    + ", Amount: " + tx.getAmount()
                    + ", Type: " + tx.getType();

            if(tx.getCategory() != null) {
                info += ", Category: " + tx.getCategory().getName();
            }
            if (tx.getToAccount() != null) {
                info += ", To Account: " + tx.getToAccount().getName();
            } else if (tx.getFromAccount() != null) {
                info += ", From Account: " + tx.getFromAccount().getName();
            }
            if(tx.getNote() != null) {
                info += ", Note: " + tx.getNote();
            }

            System.out.println(info);
        }

    }


    //test getTransactionsByAccountInRangeDate
    @Test
    public void testGetTransactionsByAccountInRangeDate() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        LedgerCategory salary = testCategories.stream()
                .filter(c -> c.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        //transaction outside range
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.of(2024, 6, 1), BigDecimal.valueOf(50.00));
        //transactions within range
        transactionController.createExpense(testLedger, testAccount, food, "Dinner", LocalDate.now(), BigDecimal.valueOf(30.00));
        transactionController.createTransfer(testLedger, testAccount, null, "Transfer to self", LocalDate.now(), BigDecimal.valueOf(100.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(3000.00));
        transactionController.createTransfer(testLedger, null, testAccount, "Transfer from self", LocalDate.now(), BigDecimal.valueOf(200.00));

        //create another account and transaction to ensure filtering works
        Account anotherAccount = accountController.createBasicAccount("Another Account", BigDecimal.valueOf(500.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "Another account notes",
                true, true);
        transactionController.createExpense(testLedger, anotherAccount, food, "Snacks", LocalDate.now(), BigDecimal.valueOf(15.00));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        List<Transaction> transactions = reportController.getTransactionsByAccountInRangeDate(testAccount, startDate, endDate);
        assertEquals(4, transactions.size());
        for (Transaction tx : transactions) {
            String info = "Transaction ID: " + tx.getId()
                    + ", Date: " + tx.getDate()
                    + ", Amount: " + tx.getAmount()
                    + ", Type: " + tx.getType();

            if(tx.getCategory() != null) {
                info += ", Category: " + tx.getCategory().getName();
            }
            if (tx.getToAccount() != null) {
                info += ", To Account: " + tx.getToAccount().getName();
            } else if (tx.getFromAccount() != null) {
                info += ", From Account: " + tx.getFromAccount().getName();
            }
            if(tx.getNote() != null) {
                info += ", Note: " + tx.getNote();
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

        List<Transaction> transactions = reportController.getTransactionsByAccountInRangeDate(testAccount, startDate, endDate);
        assertEquals(6, transactions.size());
        for (Transaction tx : transactions) {
            String info = "Transaction ID: " + tx.getId()
                    + ", Date: " + tx.getDate()
                    + ", Amount: " + tx.getAmount()
                    + ", Type: " + tx.getType();

            if(tx.getCategory() != null) {
                info += ", Category: " + tx.getCategory().getName();
            }
            if (tx.getToAccount() != null) {
                info += ", To Account: " + tx.getToAccount().getName();
            } else if (tx.getFromAccount() != null) {
                info += ", From Account: " + tx.getFromAccount().getName();
            }
            if(tx.getNote() != null) {
                info += ", Note: " + tx.getNote();
            }

            System.out.println(info);
        }

    }

    //test getIncomeByAccount and getExpenseByAccount
    @Test
    public void testGetIncomeAndExpenseByAccount() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        LedgerCategory salary = testCategories.stream()
                .filter(c -> c.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        //transaction outside range
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.of(2024, 6, 1), BigDecimal.valueOf(50.00));
        //transactions within range
        transactionController.createExpense(testLedger, testAccount, food, "Dinner", LocalDate.now(), BigDecimal.valueOf(30.00));
        transactionController.createTransfer(testLedger, testAccount, null, "Transfer to self", LocalDate.now(), BigDecimal.valueOf(100.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(3000.00));
        transactionController.createTransfer(testLedger, null, testAccount, "Transfer from self", LocalDate.now(), BigDecimal.valueOf(200.00));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        BigDecimal totalIncome = reportController.getTotalIncomeByAccount(testAccount, startDate, endDate);
        assertEquals(0, totalIncome.compareTo(BigDecimal.valueOf(3200.00))); //3000 income + 200 transfer in

        BigDecimal totalExpense = reportController.getTotalExpenseByAccount(testAccount, startDate, endDate);
        assertEquals(0, totalExpense.compareTo(BigDecimal.valueOf(130.00))); //30 expense + 100 transfer out
    }

    //test getIncomeByLedger and getExpenseByLedger
    @Test
    public void testGetIncomeAndExpenseByLedger() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        LedgerCategory salary = testCategories.stream()
                .filter(c -> c.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        //transaction outside range
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.of(2024, 6, 1), BigDecimal.valueOf(50.00));
        //transactions within range
        transactionController.createExpense(testLedger, testAccount, food, "Dinner", LocalDate.now(), BigDecimal.valueOf(30.00));
        transactionController.createTransfer(testLedger, testAccount, null, "Transfer to self", LocalDate.now(), BigDecimal.valueOf(100.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", LocalDate.now(), BigDecimal.valueOf(3000.00));
        transactionController.createTransfer(testLedger, null, testAccount, "Transfer from self", LocalDate.now(), BigDecimal.valueOf(200.00));

        LocalDate startDate = LocalDate.now().withDayOfMonth(1); //first day of current month
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()); //last day of current month

        BigDecimal totalIncome = reportController.getTotalIncomeByLedger(testLedger, startDate, endDate);
        assertEquals(0, totalIncome.compareTo(BigDecimal.valueOf(3000.00)));

        BigDecimal totalExpense = reportController.getTotalExpenseByLedger(testLedger, startDate, endDate);
        assertEquals(0, totalExpense.compareTo(BigDecimal.valueOf(30.00)));
    }

    //test getActiveBorrowingAccounts
    @Test
    public void testGetActiveBorrowingAccounts() {
        //create borrowing account not hidden
        Account borrowingAccount1 = accountController.createBorrowingAccount(testUser, "Borrowing Account 1",
                BigDecimal.valueOf(50.00), "Car loan account", true, true, testAccount,
                LocalDate.now(), testLedger);
        assertNotNull(borrowingAccount1);

        //create hidden borrowing account
        Account borrowingAccount2 = accountController.createBorrowingAccount(testUser, "Borrowing Account 2",
                BigDecimal.valueOf(30.00), "Personal loan account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(borrowingAccount2);
        accountController.hideAccount(borrowingAccount2);

        List<BorrowingAccount> activeBorrowingAccounts = reportController.getActiveBorrowingAccounts(testUser);
        assertEquals(1, activeBorrowingAccounts.size());
        assertEquals("Borrowing Account 1", activeBorrowingAccounts.getFirst().getName());
    }

    //test getActiveLendingAccounts
    @Test
    public void testGetActiveLendingAccounts() {
        //create visible lending account
        Account lendingAccount1 = accountController.createLendingAccount(testUser, "Lending Account 1",
                BigDecimal.valueOf(100.00), "Mortgage account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount1);

        //create lending account hidden
        Account lendingAccount2 = accountController.createLendingAccount(testUser, "Lending Account 2",
                BigDecimal.valueOf(200.00), "Friend loan account",
                true, true, testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount2);
        accountController.hideAccount(lendingAccount2);

        List<LendingAccount> activeLendingAccounts = reportController.getActiveLendingAccounts(testUser);
        assertEquals(1, activeLendingAccounts.size());
        assertEquals("Lending Account 1", activeLendingAccounts.getFirst().getName());
    }

    //test getAccountsNotHidden
    @Test
    public void testGetAccountsNotHidden() {
        //visible BasicAccount is testAccount created in setup
        //create hidden BasicAccount
        Account hiddenAccount = accountController.createBasicAccount("Hidden Account", BigDecimal.valueOf(300.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "Hidden account notes",
                true, true);
        assertNotNull(hiddenAccount);
        accountController.hideAccount(hiddenAccount);

        //create visible CreditAccount
        Account creditAccount1 = accountController.createCreditAccount("Credit Account 1", "Credit account notes",
                BigDecimal.valueOf(500.00), true, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(2000.00), BigDecimal.valueOf(150.00),
                1, 5);
        assertNotNull(creditAccount1);
        //create hidden CreditAccount
        Account creditAccount2 = accountController.createCreditAccount("Credit Account 2", "Another credit account notes",
                BigDecimal.valueOf(400.00), true, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(1500.00), BigDecimal.valueOf(100.00),
                1, 5);
        assertNotNull(creditAccount2);
        accountController.hideAccount(creditAccount2);

        //create visible LoanAccount
        Account loanAccount1 = accountController.createLoanAccount("Loan Account 1", "Loan account notes",
                true, testUser, 36, 0,
                BigDecimal.valueOf(1.00),  BigDecimal.valueOf(5000.00), testAccount, LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
        assertNotNull(loanAccount1);
        //create hidden LoanAccount
        Account loanAccount2 = accountController.createLoanAccount("Loan Account 2", "Another loan account notes",
                true, testUser, 24, 0,
                BigDecimal.valueOf(1.50),  BigDecimal.valueOf(3000.00), testAccount, LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger);
        assertNotNull(loanAccount2);
        accountController.hideAccount(loanAccount2);

        //create visible LendingAccount
        Account lendingAccount1 = accountController.createLendingAccount(testUser, "Lending Account 1",
                BigDecimal.valueOf(100.00), "Mortgage account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount1);
        //create hidden lending account
        Account lendingAccount2 = accountController.createLendingAccount(testUser, "Lending Account 2",
                BigDecimal.valueOf(200.00), "Friend loan account",
                true, true, testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount2);
        accountController.hideAccount(lendingAccount2);

        //create visible BorrowingAccount
        Account borrowingAccount1 = accountController.createBorrowingAccount(testUser, "Borrowing Account 1",
                BigDecimal.valueOf(50.00), "Car loan account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(borrowingAccount1);
        //create hidden borrowing account
        Account borrowingAccount2 = accountController.createBorrowingAccount(testUser, "Borrowing Account 2",
                BigDecimal.valueOf(30.00), "Personal loan account",
                true, true, testAccount, LocalDate.now(), testLedger);
        assertNotNull(borrowingAccount2);
        accountController.hideAccount(borrowingAccount2);

        List<Account> accountsNotHidden = reportController.getAccountsNotHidden(testUser);
        assertEquals(3, accountsNotHidden.size());
        for( Account acc : accountsNotHidden) {
            assertFalse(acc.getHidden());
            System.out.println("Account ID: " + acc.getId() + ", class: " + acc.getClass().getSimpleName() +
                    ", Name: " + acc.getName() + ", Type: " +acc.getType() +
                    ", Category: " + acc.getCategory());
        }
    }

    //test getLedger
    @Test
    public void testGetLedgersByUser_And_LedgerCategoryTree() {
        //visible ledger is testLedger created in setup
        //create second ledger
        Ledger secondLedger = ledgerController.createLedger("Second Ledger", testUser);
        assertNotNull(secondLedger);

        //create ledger and delete it
        Ledger deletedLedger = ledgerController.createLedger("Deleted Ledger", testUser);
        assertNotNull(deletedLedger);
        ledgerController.deleteLedger(deletedLedger);

        List<Ledger> ledgers = reportController.getLedgerByUser(testUser);
        assertEquals(2, ledgers.size());
    }

    //test getTotalAsset
    @Test
    public void testGetTotalAsset() {
        //visible BasicAccount is testAccount created in setup. it's included in net asset and selectable +
        //create second visible BasicAccount included in net asset  +
        BasicAccount basicAccount2 = accountController.createBasicAccount("Basic Account 2", BigDecimal.valueOf(200.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "Second basic account notes",
                true, true);
        assertNotNull(basicAccount2);
        //create hidden BasicAccount but included in net asset
        BasicAccount hiddenAccount = accountController.createBasicAccount("Hidden Account", BigDecimal.valueOf(300.00),
                AccountType.PASSBOOK, AccountCategory.FUNDS, testUser, "Hidden account notes",
                true, true);
        assertNotNull(hiddenAccount);
        accountController.hideAccount(hiddenAccount);
        //create visible BasicAccount but not included in net asset
        BasicAccount basicAccount = accountController.createBasicAccount("Basic Account", BigDecimal.valueOf(400.00),
                AccountType.PAYPAL, AccountCategory.FUNDS, testUser, "Unselectable account notes",
                false, true);
        assertNotNull(basicAccount);

        //create visible CreditAccount +
        CreditAccount creditAccount1 = accountController.createCreditAccount("Credit Account 1", "Credit account notes",
                BigDecimal.valueOf(500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(2000.00), //credit limit
                BigDecimal.valueOf(150.00), //current debt
                1, 5);
        assertNotNull(creditAccount1);
        //create hidden CreditAccount but included in net asset
        CreditAccount creditAccount2 = accountController.createCreditAccount("Credit Account 2", "Another credit account notes",
                BigDecimal.valueOf(400.00), true, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(1500.00), BigDecimal.valueOf(100.00),
                1, 5);
        assertNotNull(creditAccount2);
        accountController.hideAccount(creditAccount2);
        //create visible CreditAccount but not included in net asset
        CreditAccount creditAccount3 = accountController.createCreditAccount("Credit Account 3", "Third credit account notes",
                BigDecimal.valueOf(600.00), false, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(2500.00), BigDecimal.valueOf(200.00),
                1, 5);
        assertNotNull(creditAccount3);

        //create visible lending account +
        LendingAccount lendingAccount1 = accountController.createLendingAccount(testUser, "Lending Account 1",
                BigDecimal.valueOf(100.00), "Mortgage account", true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount1);
        //create hidden lending account but included in net asset
        LendingAccount lendingAccount2 = accountController.createLendingAccount(testUser, "Lending Account 2",
                BigDecimal.valueOf(200.00), null,
                true, true, testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount2);
        accountController.hideAccount(lendingAccount2);
        //create second visible lending account +
        LendingAccount lendingAccount3 = accountController.createLendingAccount(testUser, "Lending Account 3",
                BigDecimal.valueOf(150.00), null, true, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount3);
        //create visible lending account but not included in net asset
        LendingAccount lendingAccount4 = accountController.createLendingAccount(testUser, "Lending Account 4",
                BigDecimal.valueOf(250.00), null, false, true,
                testAccount, LocalDate.now(), testLedger);
        assertNotNull(lendingAccount4);
        //balance of testAccount is 1000.00-100-200-150-250=300

        //create visible borrowing account
        BorrowingAccount borrowingAccount1 = accountController.createBorrowingAccount(testUser, "Borrowing Account 1",
                BigDecimal.valueOf(50.00), "Car loan account", true, true,
                testAccount, LocalDate.now(), testLedger); //balance of testAccount is 300+50=350.00
        assertNotNull(borrowingAccount1);
        List<BorrowingAccount> borrowingAccounts = reportController.getActiveBorrowingAccounts(testUser);
        assertEquals(1, borrowingAccounts.size());

        BigDecimal totalAsset = reportController.getTotalAssets(testUser);
        List<Account> accountsNotHidden = reportController.getAccountsNotHidden(testUser);
        assertEquals(5, accountsNotHidden.size());

        List<LendingAccount> activeLendingAccounts = reportController.getActiveLendingAccounts(testUser);
        assertEquals(3, activeLendingAccounts.size());

        System.out.println("Total Asset: " + totalAsset);
        assertEquals(0, totalAsset.compareTo(BigDecimal.valueOf(1300.00))); //350 + 200 + 500 + 100 + 150 = 1300.00
    }

    //test getTotalLiability
    @Test
    public void testGetTotalLiability() {
        //create visible CreditAccount +
        CreditAccount creditAccount1 = accountController.createCreditAccount("Credit Account 1", "Credit account notes",
                BigDecimal.valueOf(500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(2000.00), //credit limit
                BigDecimal.valueOf(150.00), //current debt
                1, 5);
        assertNotNull(creditAccount1);
        //create hidden CreditAccount but included in net asset
        CreditAccount creditAccount2 = accountController.createCreditAccount("Credit Account 2", "Another credit account notes",
                BigDecimal.valueOf(400.00), true, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(1500.00), BigDecimal.valueOf(100.00),
                1, 5);
        assertNotNull(creditAccount2);
        accountController.hideAccount(creditAccount2);
        //create visible CreditAccount but not included in net asset
        CreditAccount creditAccount3 = accountController.createCreditAccount("Credit Account 3", "Third credit account notes",
                BigDecimal.valueOf(600.00), false, true, testUser,
                AccountType.CREDIT_CARD, BigDecimal.valueOf(2500.00), BigDecimal.valueOf(200.00),
                1, 5);
        assertNotNull(creditAccount3);

        //create installment (not included in current debt) for creditAccount1 +
        LedgerCategory electronics = testCategories.stream()
                .filter(c -> c.getName().equals("Electronics"))
                .findFirst()
                .orElse(null);
        assertNotNull(electronics);
        Installment installment = installmentController.createInstallment(creditAccount1, "Laptop Installment",
                BigDecimal.valueOf(1200.00), //total amount
                12,
                BigDecimal.valueOf(1.00),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), electronics, false, testLedger); //remaining amount 1212.00-101=1111.00
        assertNotNull(installment); //remaining amount 1212.00
        //create second installment (included in current debt) for creditAccount1 +
        Installment installment2 = installmentController.createInstallment(creditAccount1, "Phone Installment",
                BigDecimal.valueOf(600.00), //total amount
                6,
                BigDecimal.valueOf(1.50),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), electronics, true, testLedger); //remaining amount 609.00-101.50=507.50
        assertNotNull(installment2);

        //create visible LoanAccount +
        LoanAccount loanAccount1 = accountController.createLoanAccount("Loan Account 1", "Loan account notes",
                true, testUser, 36, 0,
                BigDecimal.valueOf(1.00),  BigDecimal.valueOf(5000.00), testAccount, LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger); //remaining amount 5077.44
        assertNotNull(loanAccount1);
        //create hidden LoanAccount but included in net asset
        LoanAccount loanAccount2 = accountController.createLoanAccount("Loan Account 2", "Another loan account notes",
                true, testUser, 24, 0,
                BigDecimal.valueOf(1.50),  BigDecimal.valueOf(3000.00), testAccount, LocalDate.now(),
                LoanAccount.RepaymentType.EQUAL_INTEREST, testLedger); //remaining amount 3047.04
        assertNotNull(loanAccount2);
        accountController.hideAccount(loanAccount2);

        //create visible borrowing account +
        BorrowingAccount borrowingAccount1 = accountController.createBorrowingAccount(testUser, "Borrowing Account 1",
                BigDecimal.valueOf(50.00), "Car loan account", true, true,
                testAccount, LocalDate.now(), testLedger); //balance of testAccount is 1000-50=950.00
        assertNotNull(borrowingAccount1);
        //create hidden borrowing account but included in net asset
        BorrowingAccount borrowingAccount2 = accountController.createBorrowingAccount(testUser, "Borrowing Account 2",
                BigDecimal.valueOf(30.00), "Personal loan account", true, true,
                testAccount, LocalDate.now(), testLedger); //balance of testAccount is 950-30=920.00
        assertNotNull(borrowingAccount2);
        accountController.hideAccount(borrowingAccount2);
        //create second visible borrowing account +
        BorrowingAccount borrowingAccount3 = accountController.createBorrowingAccount(testUser, "Borrowing Account 3",
                BigDecimal.valueOf(70.00), "Student loan account", true, true,
                testAccount, LocalDate.now(), testLedger); //balance of testAccount is 920-70=850.00
        assertNotNull(borrowingAccount3);
        //create visible borrowing account but not included in net asset
        BorrowingAccount borrowingAccount4 = accountController.createBorrowingAccount(testUser, "Borrowing Account 4",
                BigDecimal.valueOf(90.00), "Credit card account", false, true,
                testAccount, LocalDate.now(), testLedger); //balance of testAccount is 850-90=760.00
        assertNotNull(borrowingAccount4);

        BigDecimal totalLiability = reportController.getTotalLiabilities(testUser);
        List<BorrowingAccount> activeBorrowingAccounts = reportController.getActiveBorrowingAccounts(testUser);
        assertEquals(3, activeBorrowingAccounts.size());

        assertEquals(0, totalLiability.compareTo(BigDecimal.valueOf(6965.94))); //150 + 1111.00 + 507.50 + 5077.44 + 50 + 70 = 6965.94
    }

    //test getInstallments
    @Test
    public void testGetInstallments() {
        LedgerCategory electronics = testCategories.stream()
                .filter(c -> c.getName().equals("Electronics"))
                .findFirst()
                .orElse(null);
        assertNotNull(electronics);

        //create CreditAccount
        CreditAccount creditAccount = accountController.createCreditAccount("Credit Account", "Credit account notes",
                BigDecimal.valueOf(500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(2000.00), //credit limit
                BigDecimal.valueOf(150.00), //current debt
                1, 5);
        assertNotNull(creditAccount);

        //create active installment
        Installment installment1 = installmentController.createInstallment(creditAccount, "Installment 1",
                BigDecimal.valueOf(600.00), //total amount
                6,
                BigDecimal.valueOf(2.00),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), electronics, true, testLedger);
        assertNotNull(installment1);
        //create completed installment
        Installment installmentCompleted = installmentController.createInstallment(creditAccount, "Completed Installment",
                BigDecimal.valueOf(300.00), //total amount
                3,
                BigDecimal.valueOf(1.50),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now().minusMonths(4), //4 months ago
                electronics, true, testLedger);
        assertNotNull(installmentCompleted);

        //create second credit account
        CreditAccount creditAccount2 = accountController.createCreditAccount("Credit Account 2", "Another credit account notes",
                BigDecimal.valueOf(400.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(1500.00), //credit limit
                BigDecimal.valueOf(100.00), //current debt
                1, 5);
        assertNotNull(creditAccount2);
        //create active installment for second credit account
        Installment installment2 = installmentController.createInstallment(creditAccount2, "Installment 2",
                BigDecimal.valueOf(300.00), //total amount
                3,
                BigDecimal.valueOf(1.50),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), electronics, true, testLedger);
        assertNotNull(installment2);

        List<Installment> installments = reportController.getActiveInstallments(creditAccount);
        assertEquals(1, installments.size());
        for( Installment inst : installments) {
            System.out.println("Installment ID: " + inst.getId() +
                    ", Name: " + inst.getName() +
                    ", Total Amount: " + inst.getTotalAmount() +
                    ", Paid Periods: " + inst.getPaidPeriods() +
                    ", Total Periods: " + inst.getTotalPeriods() +
                    ", Category: " +inst.getCategory().getName());
        }
    }

    //test LedgerCategory tree structure
    @Test
    public void testLedgerCategoryTreeStructure() {
        List<LedgerCategory> categories = reportController.getLedgerCategoryTreeByLedger(testLedger);
        assertEquals(17, categories.size());

        List<LedgerCategory> rootCategories = categories.stream()
                .filter(c -> c.getParent() == null)
                .toList();
        assertEquals(12, rootCategories.size());

        List<LedgerCategory> incomeRootCategories = rootCategories.stream()
                .filter(c -> c.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(3, incomeRootCategories.size());

        List<LedgerCategory> expenseRootCategories = rootCategories.stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .toList();
        assertEquals(9, expenseRootCategories.size());

        System.out.println("Expense Category Tree:");
        for (LedgerCategory category : expenseRootCategories) {
            System.out.println("Category ID: " + category.getId() +
                    ", Name: " + category.getName() +
                    ", Parent: " + (category.getParent() != null ? category.getParent().getName() : "null"));
            for (LedgerCategory subcategory : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList()) {
                System.out.println("  Subcategory ID: " + subcategory.getId() +
                        ", Name: " + subcategory.getName() +
                        ", Parent: " + subcategory.getParent().getName() );
            }
        }

        System.out.println("Income Category Tree:");
        for( LedgerCategory category : incomeRootCategories) {
            System.out.println("Category ID: " + category.getId() +
                    ", Name: " + category.getName() +
                    ", Parent: " + (category.getParent() != null ? category.getParent().getName() : "null"));
            for (LedgerCategory subcategory : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList()) {
                System.out.println("  Subcategory ID: " + subcategory.getId() +
                        ", Name: " + subcategory.getName() +
                        ", Parent: " +  subcategory.getParent().getName());
            }
        }
    }

    //test getActiveBudgetsByLedger
    @Test
    public void testGetActiveBudgetsByLedger() {
        //get monthly budget for ledger
        Budget budget1 = reportController.getActiveBudgetByLedger(testLedger, Budget.Period.MONTHLY);
        assertNotNull(budget1);
        assertEquals(Budget.Period.MONTHLY, budget1.getPeriod());
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));

        //get yearly budget for ledger
        Budget budget2 = reportController.getActiveBudgetByLedger(testLedger, Budget.Period.YEARLY);
        assertNotNull(budget2);
        assertEquals(Budget.Period.YEARLY, budget2.getPeriod());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByLedger if budgets are expired
    @Test
    public void testGetActiveBudgetsByLedger_ExpiredBudgets() throws SQLException{
        Budget budget = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        //set start and end date to past to simulate expired budget
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        //set amount to non-zero
        budget.setAmount(BigDecimal.valueOf(500.00));
        budgetDAO.update(budget); //persist changes

        Budget activeBudget = reportController.getActiveBudgetByLedger(testLedger, Budget.Period.MONTHLY);
        assertNotNull(activeBudget);
        assertEquals(Budget.Period.MONTHLY, activeBudget.getPeriod());
        assertEquals(0, activeBudget.getAmount().compareTo(BigDecimal.ZERO));

        Budget budget2 = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.YEARLY);
        assertNotNull(budget2);
        //set start and end date to past to simulate expired budget
        budget2.setStartDate(LocalDate.of(2023, 1, 1));
        budget2.setEndDate(LocalDate.of(2023, 12, 31));
        //set amount to non-zero
        budget2.setAmount(BigDecimal.valueOf(2000.00));
        budgetDAO.update(budget2); //persist changes

        Budget activeBudget2 = reportController.getActiveBudgetByLedger(testLedger, Budget.Period.YEARLY);
        assertNotNull(activeBudget2);
        assertEquals(Budget.Period.YEARLY, activeBudget2.getPeriod());
        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory
    @Test
    public void testGetActiveBudgetsByCategory() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        //get monthly budget for food category
        Budget budget1 = reportController.getActiveBudgetByCategory(food, Budget.Period.MONTHLY);
        assertNotNull(budget1);
        assertEquals(Budget.Period.MONTHLY, budget1.getPeriod());
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));

        //get yearly budget for food category
        Budget budget2 = reportController.getActiveBudgetByCategory(food, Budget.Period.YEARLY);
        assertNotNull(budget2);
        assertEquals(Budget.Period.YEARLY, budget2.getPeriod());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory (first level) if budgets are expired
    @Test
    public void testGetActiveBudgetsByCategory_ExpiredBudgets() throws SQLException{
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        Budget budget = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        //set start and end date to past to simulate expired budget
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        //set amount to non-zero
        budget.setAmount(BigDecimal.valueOf(300.00));
        budgetDAO.update(budget); //persist changes

        Budget activeBudget = reportController.getActiveBudgetByCategory(food, Budget.Period.MONTHLY);
        assertNotNull(activeBudget);
        assertEquals(Budget.Period.MONTHLY, activeBudget.getPeriod());
        assertEquals(0, activeBudget.getAmount().compareTo(BigDecimal.ZERO));

        Budget budget2 = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.YEARLY);
        assertNotNull(budget2);
        //set start and end date to past to simulate expired budget
        budget2.setStartDate(LocalDate.of(2023, 1, 1));
        budget2.setEndDate(LocalDate.of(2023, 12, 31));
        //set amount to non-zero
        budget2.setAmount(BigDecimal.valueOf(1500.00));
        budgetDAO.update(budget2); //persist changes

        Budget activeBudget2 = reportController.getActiveBudgetByCategory(food, Budget.Period.YEARLY);
        assertNotNull(activeBudget2);
        assertEquals(Budget.Period.YEARLY, activeBudget2.getPeriod());
        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory (second level)
    @Test
    public void testGetActiveBudgetsByCategory_SecondLevel() {
        LedgerCategory lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);

        //get monthly budget for lunch category
        Budget budget1 = reportController.getActiveBudgetByCategory(lunch, Budget.Period.MONTHLY);
        assertNotNull(budget1);
        assertEquals(Budget.Period.MONTHLY, budget1.getPeriod());
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));

        //get yearly budget for lunch category
        Budget budget2 = reportController.getActiveBudgetByCategory(lunch, Budget.Period.YEARLY);
        assertNotNull(budget2);
        assertEquals(Budget.Period.YEARLY, budget2.getPeriod());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory (second level) if budgets are expired
    @Test
    public void testGetActiveBudgetsByCategory_SecondLevel_ExpiredBudgets() throws SQLException{
        LedgerCategory lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);
        Budget budget = budgetDAO.getBudgetByCategoryId(lunch.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        //set start and end date to past to simulate expired budget
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        //set amount to non-zero
        budget.setAmount(BigDecimal.valueOf(400.00));
        budgetDAO.update(budget); //persist changes

        Budget activeBudget = reportController.getActiveBudgetByCategory(lunch, Budget.Period.MONTHLY);
        assertNotNull(activeBudget);
        assertEquals(Budget.Period.MONTHLY, activeBudget.getPeriod());
        assertEquals(0, activeBudget.getAmount().compareTo(BigDecimal.ZERO));

        Budget budget2 = budgetDAO.getBudgetByCategoryId(lunch.getId(), Budget.Period.YEARLY);
        assertNotNull(budget2);
        //set start and end date to past to simulate expired budget
        budget2.setStartDate(LocalDate.of(2023, 1, 1));
        budget2.setEndDate(LocalDate.of(2023, 12, 31));
        //set amount to non-zero
        budget2.setAmount(BigDecimal.valueOf(1800.00));
        budgetDAO.update(budget2); //persist changes
        Budget activeBudget2 = reportController.getActiveBudgetByCategory(lunch, Budget.Period.YEARLY);
        assertNotNull(activeBudget2);
        assertEquals(Budget.Period.YEARLY, activeBudget2.getPeriod());
        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test is_over_budget
    @Test
    public void testIsOverBudget() throws SQLException{
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        Budget budget = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(200.00)); //set monthly budget to 200

        LedgerCategory lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);
        Budget budget2 = budgetDAO.getBudgetByCategoryId(lunch.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget2);
        budgetController.editBudget(budget2, BigDecimal.valueOf(500.00)); //set monthly budget to 500

        //add transactions to exceed budget of food category
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));
        transactionController.createExpense(testLedger, testAccount, lunch, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));

        boolean isOverBudget = reportController.isOverBudget(budget); //expense of food is 150+150=300 > 200
        assertTrue(isOverBudget);
        System.out.println("Budget Amount: " + budget.getAmount()+
                ", Period: " + budget.getPeriod() +
                ", Category: " + budget.getCategory().getName() +
                ", Start Date: " + budget.getStartDate() +
                ", End Date: " + budget.getEndDate());

        //not exceed budget of ledger
        assertFalse(reportController.isOverBudget(budget2)); //expense of lunch is 150 < 500
        System.out.println("Budget Amount: " + budget2.getAmount()+
                ", Period: " + budget2.getPeriod() +
                ", Start Date: " + budget2.getStartDate() +
                ", End Date: " + budget2.getEndDate() +
                ", Is Over Budget: " + reportController.isOverBudget(budget2));
    }

    @Test
    public void testIsOverBudget_NoTransactions_False() throws SQLException{
        LedgerCategory transport = testCategories.stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        assertNotNull(transport);
        Budget budget = budgetDAO.getBudgetByCategoryId(transport.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(300.00));

        //ledger-level budget
        Budget budget2 = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget2);
        budgetController.editBudget(budget2, BigDecimal.valueOf(800.00));

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertFalse(isOverBudget);
        assertFalse(reportController.isOverBudget(budget2));
    }

    @Test
    public void testIsOverBudget_ExpiredBudget() throws SQLException{
        LedgerCategory entertainment = testCategories.stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        assertNotNull(entertainment);
        Budget budget = budgetDAO.getBudgetByCategoryId(entertainment.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00));
        //simulate expired budget by setting start and end date in the past
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(budget); //persist changes

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertFalse(isOverBudget);

        Budget updatedBudget = budgetDAO.getById(budget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedBudget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedBudget.getEndDate());
    }

    @Test
    public void testIsOverBudget_TransactionOverPeriod_False() throws SQLException {
        LedgerCategory entertainment = testCategories.stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        assertNotNull(entertainment);
        Budget budget = budgetDAO.getBudgetByCategoryId(entertainment.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00));

        transactionController.createExpense(testLedger, testAccount, entertainment, "Movie", LocalDate.of(2025, 1, 1), BigDecimal.valueOf(500.00));

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertFalse(isOverBudget); //should be false as budget period is over
    }

    @Test
    public void testIsOverBudget_BoundaryCase() throws SQLException {
        Budget budget1 = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget1);
        budgetController.editBudget(budget1, BigDecimal.valueOf(300.00));

        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        Budget budget = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(120.00));

        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(21.00));

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertTrue(isOverBudget);
        assertFalse(reportController.isOverBudget(budget1));

        LedgerCategory transport = testCategories.stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        transactionController.createExpense(testLedger, testAccount, transport, "Bus ticket", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, transport, "Taxi", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(201.00));
        assertTrue(reportController.isOverBudget(budget1));
    }
}

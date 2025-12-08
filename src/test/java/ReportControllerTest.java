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
    private InstallmentController  installmentController;
    private ReimbursementController reimbursementController;

    @BeforeEach
    public void setUp(){
        connection= ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        TransactionDAO transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        InstallmentDAO installmentDAO = new InstallmentDAO(connection, ledgerCategoryDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);
        ReimbursementDAO reimbursementDAO = new ReimbursementDAO(connection, ledgerCategoryDAO, accountDAO, transactionDAO);
        ReimbursementTxLinkDAO reimbursementTxLinkDAO = new ReimbursementTxLinkDAO(connection, transactionDAO, reimbursementDAO);
        DebtPaymentDAO debtPaymentDAO = new DebtPaymentDAO(connection, transactionDAO);
        InstallmentPaymentDAO installmentPaymentDAO = new InstallmentPaymentDAO(connection, transactionDAO,
                installmentDAO);
        LoanTxLinkDAO loanTxLinkDAO = new LoanTxLinkDAO(connection, transactionDAO);
        BorrowingTxLinkDAO borrowingTxLinkDAO = new BorrowingTxLinkDAO(connection, transactionDAO);
        LendingTxLinkDAO lendingTxLinkDAO = new LendingTxLinkDAO(connection, transactionDAO);

        budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, reimbursementDAO,
                reimbursementTxLinkDAO, debtPaymentDAO, installmentPaymentDAO, installmentDAO, borrowingTxLinkDAO, loanTxLinkDAO, lendingTxLinkDAO);
        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO, loanTxLinkDAO, borrowingTxLinkDAO, lendingTxLinkDAO);
        reportController = new ReportController(transactionDAO, accountDAO, budgetDAO,
                installmentDAO, ledgerCategoryDAO, reimbursementDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO,
                accountDAO, budgetDAO);
        installmentController = new InstallmentController(installmentDAO, transactionDAO, accountDAO, installmentPaymentDAO);
        reimbursementController = new ReimbursementController(transactionDAO, reimbursementDAO,
                reimbursementTxLinkDAO, ledgerCategoryDAO, accountDAO);

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

    //test getTotalPendingAmount, test getTotalReimbursedAmount, test getTotalIncome, getTotalExpense by account and by ledger when there are reimbursement's transactions
    @Test
    public void testReimbursements(){
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        //full reimbursement claimed
        Reimbursement reimbursement1 = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, food);
        assertNotNull(reimbursement1);
        reimbursementController.claim(reimbursement1, BigDecimal.valueOf(500.00), true, testAccount, null);

        //partial reimbursement claimed
        Reimbursement reimbursement2 = reimbursementController.create(BigDecimal.valueOf(300.00), testAccount,
                testLedger, food);
        assertNotNull(reimbursement2);
        reimbursementController.claim(reimbursement2, BigDecimal.valueOf(200.00), false, testAccount, LocalDate.now());

        //final partial reimbursement claimed
        Reimbursement reimbursement3 = reimbursementController.create(BigDecimal.valueOf(300.00), testAccount, testLedger, food);
        assertNotNull(reimbursement3);
        reimbursementController.claim(reimbursement3, BigDecimal.valueOf(200.00), true, testAccount, LocalDate.now());

        //over-claim reimbursement
        Reimbursement reimbursement4 = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount, testLedger, food);
        assertNotNull(reimbursement4);
        reimbursementController.claim(reimbursement4, BigDecimal.valueOf(400.00), true, testAccount, null);

        BigDecimal totalPending = reportController.getTotalPendingAmount(testLedger);
        assertEquals(0, totalPending.compareTo(BigDecimal.valueOf(100.00))); //300-200=100 pending

        BigDecimal totalReimbursed = reportController.getTotalReimbursedAmount(testLedger);
        assertEquals(0, totalReimbursed.compareTo(BigDecimal.valueOf(1100.00)));

        BigDecimal totalExpenseOfLedger = reportController.getTotalExpenseByLedger(testLedger,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        assertEquals(0, totalExpenseOfLedger.compareTo(BigDecimal.valueOf(100.00)));

        BigDecimal totalIncomeOfLedger = reportController.getTotalIncomeByLedger(testLedger,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        assertEquals(0, totalIncomeOfLedger.compareTo(BigDecimal.valueOf(200.00)));

        BigDecimal totalExpenseOfAccount = reportController.getTotalExpenseByAccount(testAccount,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        assertEquals(0, totalExpenseOfAccount.compareTo(BigDecimal.valueOf(1300.00)));

        BigDecimal totalIncomeOfAccount = reportController.getTotalIncomeByAccount(testAccount,
                LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        assertEquals(0, totalIncomeOfAccount.compareTo(BigDecimal.valueOf(1300.00)));
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

        BigDecimal totalAsset = reportController.getTotalAssets(testUser);
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
        assertEquals(0, totalLiability.compareTo(BigDecimal.valueOf(6965.94))); //150 + 1111.00 + 507.50 + 5077.44 + 50 + 70 = 6965.94
    }

    //test is_over_budget
    @Test
    public void testIsOverBudget() {
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
    public void testIsOverBudget_NoTransactions_False() {
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
    public void testIsOverBudget_ExpiredBudget() {
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
    public void testIsOverBudget_TransactionOverPeriod_False() {
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
    public void testIsOverBudget_BoundaryCase() {
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

    //test getTotalIncome and getTotalExpense when there are reimbursement transactions
    @Test
    public void testFullReimbursementClaim() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, food);

        // Claim full reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                true, testAccount1, null);
        assertTrue(claimResult);

        assertEquals(0, reportController.getTotalExpenseByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.ZERO));
        assertEquals(0, reportController.getTotalIncomeByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testPartialReimbursementClaim() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, food);

        // Claim partial reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                false, testAccount1, LocalDate.now());
        assertTrue(claimResult);

        assertEquals(0, reportController.getTotalExpenseByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.ZERO));
        assertEquals(0, reportController.getTotalIncomeByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.ZERO));
    }

    //test partial reimbursement claim but is final claim
    @Test
    public void testPartialFinalReimbursementClaim() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(500.00), testAccount,
                testLedger, food);

        // Claim partial reimbursement
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(200.00),
                true, testAccount1, LocalDate.now());
        assertTrue(claimResult);

        assertEquals(0, reportController.getTotalExpenseByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(0, reportController.getTotalIncomeByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.ZERO));
    }

    //test getTotalExpenseByLedger and getTotalIncomeByLedger when over-claimed reimbursement
    @Test
    public void testOverClaimReimbursement() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        BasicAccount testAccount1 = accountController.createBasicAccount("Reimbursement Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, null, true, true);

        Reimbursement reimbursement = reimbursementController.create(BigDecimal.valueOf(200.00), testAccount,
                testLedger, food);

        // Attempt to claim more than the remaining amount
        boolean claimResult = reimbursementController.claim(reimbursement, BigDecimal.valueOf(400.00),
                true, testAccount1, null);
        assertTrue(claimResult);

        assertEquals(0, reportController.getTotalExpenseByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.ZERO));
        assertEquals(0, reportController.getTotalIncomeByLedger(testLedger, LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())).compareTo(BigDecimal.valueOf(200.00)));
    }
}

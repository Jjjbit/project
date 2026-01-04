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
    private LedgerCategory food;
    private LedgerCategory salary;
    private LedgerCategory lunch;
    private LedgerCategory entertainment;
    private LedgerCategory transport;

    private BudgetDAO budgetDAO;

    private BudgetController budgetController;
    private TransactionController transactionController;
    private ReportController reportController;
    private AccountController accountController;

    @BeforeEach
    public void setUp(){
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        TransactionDAO transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        budgetDAO = new BudgetDAO(connection);

        budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO);
        reportController = new ReportController(transactionDAO, accountDAO, budgetDAO, ledgerCategoryDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO,
                accountDAO, budgetDAO);

        userController.register("test user", "password123");
        testUser = userController.login("test user", "password123");
        testLedger = ledgerController.createLedger("Test Ledger");
        List<LedgerCategory> testCategories = ledgerCategoryDAO.getTreeByLedger(testLedger);
        food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        salary = testCategories.stream()
                .filter(c -> c.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        entertainment = testCategories.stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        transport = testCategories.stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        testAccount = accountController.createAccount("Test Account", BigDecimal.valueOf(1000.00), true, true);
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

    //test getIncomeByAccount and getExpenseByAccount
    @Test
    public void testGetIncomeAndExpenseByAccount() {
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

    //test getTotalAssets
    @Test
    public void testGetTotalAssets() {
        assertEquals(0, reportController.getTotalAssets(testUser).compareTo(BigDecimal.valueOf(1000.00))); // total assets = balance of testAccount
        accountController.createAccount("Second Account", BigDecimal.valueOf(500.00), true, true); //create another account
        assertEquals(0, reportController.getTotalAssets(testUser).compareTo(BigDecimal.valueOf(1500.00)));
    }

    //test is_over_budget
    @Test
    public void testIsOverBudget() {
        Budget budget = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(200.00)); //set monthly budget to 200

        Budget budget2 = budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY);
        assertNotNull(budget2);
        budgetController.editBudget(budget2, BigDecimal.valueOf(500.00)); //set monthly budget to 500

        //add transactions to exceed budget of food category
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));
        transactionController.createExpense(testLedger, testAccount, lunch, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));

        boolean isOverBudget = reportController.isOverBudget(budget); //expense of food is 150+150=300 > 200
        assertTrue(isOverBudget);
//        System.out.println("Budget Amount: " + budget.getAmount()+
//                ", Period: " + budget.getPeriod() +
//                ", Category: " + budget.getCategory().getName() +
//                ", Start Date: " + budget.getStartDate() +
//                ", End Date: " + budget.getEndDate());
//
//        //not exceed budget of ledger
//        assertFalse(reportController.isOverBudget(budget2)); //expense of lunch is 150 < 500
//        System.out.println("Budget Amount: " + budget2.getAmount()+
//                ", Period: " + budget2.getPeriod() +
//                ", Start Date: " + budget2.getStartDate() +
//                ", End Date: " + budget2.getEndDate() +
//                ", Is Over Budget: " + reportController.isOverBudget(budget2));
    }

    @Test
    public void testIsOverBudget_NoTransactions_False() {
        Budget budget = budgetDAO.getBudgetByCategory(transport, Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(300.00));

        //ledger-level budget
        Budget budget2 = budgetDAO.getBudgetByLedger(testLedger, Period.MONTHLY);
        assertNotNull(budget2);
        budgetController.editBudget(budget2, BigDecimal.valueOf(800.00));

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertFalse(isOverBudget);
        assertFalse(reportController.isOverBudget(budget2));
    }

    @Test
    public void testIsOverBudget_ExpiredBudget() {
        Budget budget = budgetDAO.getBudgetByCategory(entertainment, Period.MONTHLY);
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
        Budget budget = budgetDAO.getBudgetByCategory(entertainment, Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00));

        transactionController.createExpense(testLedger, testAccount, entertainment, "Movie", LocalDate.of(2025, 1, 1), BigDecimal.valueOf(500.00));

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertFalse(isOverBudget); //should be false as budget period is over
    }

    @Test
    public void testIsOverBudget_BoundaryCase() {
        Budget budget1 = budgetDAO.getBudgetByLedger(testLedger, Period.MONTHLY);
        assertNotNull(budget1);
        budgetController.editBudget(budget1, BigDecimal.valueOf(300.00));

        Budget budget = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(120.00));

        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(21.00));

        boolean isOverBudget = reportController.isOverBudget(budget);
        assertTrue(isOverBudget);
        assertFalse(reportController.isOverBudget(budget1));

        transactionController.createExpense(testLedger, testAccount, transport, "Bus ticket", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, transport, "Taxi", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(201.00));
        assertTrue(reportController.isOverBudget(budget1));
    }
}

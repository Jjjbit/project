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

        LocalDate startDate = LocalDate.now().withDayOfMonth(1); //first day of current month
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()); //last day of current month

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
        budgetController.editBudget(budget, BigDecimal.valueOf(200.00)); //set monthly food budget to 200

        Budget budget2 = budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY);
        budgetController.editBudget(budget2, BigDecimal.valueOf(100.00)); //set monthly lunch budget to 100

        //add transactions to exceed budget of food category
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00)); //expense of food is 150 < 200
        transactionController.createExpense(testLedger, testAccount, lunch, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00)); //expense of lunch is 150 > 100

        assertTrue(reportController.isOverBudget(budget)); //expense of food is 150+150=300 > 200
        assertTrue(reportController.isOverBudget(budget2)); //expense of lunch is 150 > 100
    }

    @Test
    public void testIsOverBudget_NoTransactions_False() {
        Budget budget = budgetDAO.getBudgetByCategory(transport, Period.MONTHLY); //get monthly transport budget for testLedger
        budgetController.editBudget(budget, BigDecimal.valueOf(300.00)); //set monthly transport budget to 300

        Budget budget2 = budgetDAO.getBudgetByLedger(testLedger, Period.MONTHLY); //get monthly total budget for testLedger
        budgetController.editBudget(budget2, BigDecimal.valueOf(800.00)); //set monthly total budget to 800

        assertFalse(reportController.isOverBudget(budget));
        assertFalse(reportController.isOverBudget(budget2));
    }

    @Test
    public void testIsOverBudget_ExpiredBudget() {
        Budget budget = budgetDAO.getBudgetByCategory(entertainment, Period.MONTHLY);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00)); //set monthly entertainment budget to 400
        //simulate expired budget by setting start and end date in the past
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(budget); //persist changes

        transactionController.createExpense(testLedger, testAccount, entertainment, "Concert", LocalDate.of(2025, 1, 20), BigDecimal.valueOf(500.00)); //transaction in past period
        assertFalse(reportController.isOverBudget(budget));

        Budget updatedBudget = budgetDAO.getById(budget.getId()); //get updated budget from database
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.ZERO)); //amount should be reset to 0
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedBudget.getStartDate()); //start date should be updated to first day of current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedBudget.getEndDate()); //end date should be updated to last day of current month
    }

    @Test
    public void testIsOverBudget_TransactionOverPeriod_False() {
        Budget budget = budgetDAO.getBudgetByCategory(entertainment, Period.MONTHLY);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00)); //set monthly entertainment budget to 400
        transactionController.createExpense(testLedger, testAccount, entertainment, null, LocalDate.of(2025, 1, 1), BigDecimal.valueOf(500.00)); //transaction outside budget period
        assertFalse(reportController.isOverBudget(budget)); //should be false as budget period is over
    }

    @Test
    public void testIsOverBudget_BoundaryCase() {
        Budget budget1 = budgetDAO.getBudgetByLedger(testLedger, Period.MONTHLY);
        budgetController.editBudget(budget1, BigDecimal.valueOf(300.00)); //set monthly total budget to 300

        Budget budget = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
        budgetController.editBudget(budget, BigDecimal.valueOf(120.00)); //set monthly food budget to 120

        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00)); //first day of month
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(21.00)); //last day of month

        assertTrue(reportController.isOverBudget(budget));
        assertFalse(reportController.isOverBudget(budget1));

        transactionController.createExpense(testLedger, testAccount, transport, "Bus ticket", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, transport, "Taxi", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(201.00));
        assertTrue(reportController.isOverBudget(budget1));
    }
}

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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private Account testAccount;

    private UserDAO userDAO;
    private BudgetDAO budgetDAO;
    private LedgerDAO ledgerDAO;
    private TransactionDAO transactionDAO;
    private CategoryDAO categoryDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private AccountDAO accountDAO;
    private InstallmentPlanDAO installmentPlanDAO;

    private UserController userController;
    private BudgetController budgetController;
    private LedgerController ledgerController;
    private TransactionController transactionController;
    private AccountController accountController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        budgetDAO = new BudgetDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        categoryDAO = new CategoryDAO(connection);
        accountDAO = new AccountDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        installmentPlanDAO = new InstallmentPlanDAO(connection);

        userController = new UserController(userDAO);
        budgetController = new BudgetController(budgetDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
        accountController = new AccountController(accountDAO, transactionDAO, installmentPlanDAO);

        userController.register("testuser", "password");
        testUser =userController.login("testuser", "password");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

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

    private void executeSqlFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            String sql = Files.lines(path)
                    .collect(Collectors.joining("\n"));
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

    //edit monthly ledger-level budget
    @Test
    public void testEditBudget_Success() throws SQLException {
        Budget budget = testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO));

        boolean result = budgetController.editBudget(budget, BigDecimal.valueOf(600.00));
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(600.00)));

        Budget updatedBudget= budgetDAO.getById(budget.getId()); //fetch from DB to verify
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(600.00)));
    }

    //edit monthly category-level budget
    @Test
    public void testEditBudget_WithCategory_Success()  {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        Budget budget = testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO));

        boolean result = budgetController.editBudget(budget, BigDecimal.valueOf(450.00));
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(450.00)));
    }

    //merge category-level budget to uncategorized budget
    @Test
    public void testMergeBudgets_Success() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        LedgerCategory lunch = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);

        Budget budget1 = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        Budget budget2 = testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget2, BigDecimal.valueOf(300.00));

        Budget budget3 = lunch.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY)
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget3, BigDecimal.valueOf(100.00));


        boolean result = budgetController.mergeBudgets(budget2);
        assertTrue(result);
        System.out.println("Merged Budget Amount: " + budget2.getAmount());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.valueOf(500.00)));

        Budget updatedBudget=budgetDAO.getById(budget2.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(500.00)));
    }

    @Test
    public void testMergeBudgets_DifferentPeriods() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        Budget ledgerBudget=testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.YEARLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(ledgerBudget, BigDecimal.valueOf(100.00));

        boolean result = budgetController.mergeBudgets(ledgerBudget);
        assertTrue(result);
        assertEquals(0, ledgerBudget.getAmount().compareTo(BigDecimal.valueOf(100.00))); //amount should remain unchanged
    }

    //merge subcategory-level budget to category-level budget
    @Test
    public void testMergeBudgets_WithCategory_Success() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        LedgerCategory dinner = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Dinner"))
                .findFirst()
                .orElse(null);
        Budget budget2 = dinner.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY)
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget2, BigDecimal.valueOf(150.00));

        boolean result = budgetController.mergeBudgets(budget1);
        assertTrue(result);
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.valueOf(350.00)));
    }


    @Test
    public void testMergeBudgets_ExpiredBudgets_Case1() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        //simulate expired budget by setting start and end date in the past
        budget1.setStartDate(LocalDate.of(2025, 1, 1));
        budget1.setEndDate(LocalDate.of(2025, 1, 31));

        Budget ledgerBudget=testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(ledgerBudget, BigDecimal.valueOf(300.00));
        //simulate expired budget by setting start and end date in the past
        ledgerBudget.setStartDate(LocalDate.of(2025, 1, 1));
        ledgerBudget.setEndDate(LocalDate.of(2025, 1, 31));

        boolean result = budgetController.mergeBudgets(ledgerBudget);
        assertTrue(result);
        assertEquals(0, ledgerBudget.getAmount().compareTo(BigDecimal.ZERO)); //0+0=0

        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO)); //should be reset to 0 after refresh
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget1.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget1.getEndDate()); //should be refreshed to

        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), ledgerBudget.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), ledgerBudget.getEndDate()); //should be refreshed to current month
    }


    @Test
    public void testMergeBudgets_ExpiredBudgets_Case2() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        budget1.setStartDate(LocalDate.of(2025, 1, 1));
        budget1.setEndDate(LocalDate.of(2025, 1, 31));

        LedgerCategory lunch = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        Budget lunchBudget=lunch.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY)
                .findFirst()
                .orElse(null);
        budgetController.editBudget(lunchBudget, BigDecimal.valueOf(300.00));

        lunchBudget.setStartDate(LocalDate.of(2025, 1, 1));
        lunchBudget.setEndDate(LocalDate.of(2025, 1, 31));


        boolean result = budgetController.mergeBudgets(budget1);
        assertTrue(result);
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO)); //0+0=0
        assertEquals(0, lunchBudget.getAmount().compareTo(BigDecimal.ZERO)); //should be reset to 0 after refresh
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), lunchBudget.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), lunchBudget.getEndDate()); //should be refreshed to current month

        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget1.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget1.getEndDate()); //should be refreshed to current month
    }

    //test is_over_budget
    @Test
    public void testIsOverBudget() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(200.00));

        LedgerCategory lunch = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);

        Budget budget2 = lunch.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget2, BigDecimal.valueOf(500.00));

        //add transactions to exceed budget of food category
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));
        transactionController.createExpense(testLedger, testAccount, lunch, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));

        boolean isOverBudget = budgetController.isOverBudget(budget);
        assertTrue(isOverBudget);

        //not exceed budget of ledger
        assertFalse(budgetController.isOverBudget(budget2));

    }

    @Test
    public void testIsOverBudget_NoTransactions_False() {
        LedgerCategory transport = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        Budget budget = transport.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(300.00));

        //ledger-level budget
        Budget budget2 =testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget2, BigDecimal.valueOf(800.00));

        boolean isOverBudget = budgetController.isOverBudget(budget);
        assertFalse(isOverBudget);
        assertFalse(budgetController.isOverBudget(budget2));
    }

    @Test
    public void testIsOverBudget_ExpiredBudget() {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        Budget budget = entertainment.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00));

        //simulate expired budget by setting start and end date in the past
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));

        boolean isOverBudget = budgetController.isOverBudget(budget);
        assertFalse(isOverBudget);

        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO)); //should be reset to 0 after refresh
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate()); //should be refreshed to current month
    }

    @Test
    public void testIsOverBudget_TransactionOverPeriod_False() {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        Budget budget = entertainment.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(400.00));

        transactionController.createExpense(testLedger, testAccount, entertainment, "Movie", LocalDate.of(2025, 1, 1), BigDecimal.valueOf(500.00));

        boolean isOverBudget = budgetController.isOverBudget(budget);
        assertFalse(isOverBudget); //should be false as budget period is over
    }

    @Test
    public void testIsOverBudget_BoundaryCase() {
        Budget budget1 = testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget1, BigDecimal.valueOf(300.00));

        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget = food.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(120.00));

        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(21.00));

        boolean isOverBudget = budgetController.isOverBudget(budget);
        assertTrue(isOverBudget);
        assertFalse(budgetController.isOverBudget(budget1));

        LedgerCategory transport = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        transactionController.createExpense(testLedger, testAccount, transport, "Bus ticket", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, transport, "Taxi", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(201.00));
        assertTrue(budgetController.isOverBudget(budget1));
    }

    //refresh budget test
    @Test
    public void testRefreshBudget_ExpiredBudget() {
        Budget budget = testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(500.00));
        //simulate expired budget by setting start and end date in the past
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));

        boolean result = budgetController.refreshBudget(budget);
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO)); //should be reset to 0 after refresh
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate()); //should be refreshed to current month
    }

    @Test
    public void testRefreshBudget_ActiveBudget() {
        Budget budget = testLedger.getBudgets().stream()
                .filter(b -> b.getPeriod() == Budget.Period.MONTHLY )
                .findFirst()
                .orElse(null);
        budgetController.editBudget(budget, BigDecimal.valueOf(500.00));
        LocalDate originalStartDate = budget.getStartDate();
        LocalDate originalEndDate = budget.getEndDate();

        boolean result = budgetController.refreshBudget(budget);
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(500.00))); //amount should remain unchanged
        assertEquals(originalStartDate, budget.getStartDate()); //start date should remain unchanged
        assertEquals(originalEndDate, budget.getEndDate()); //end date should remain unchanged
    }
}

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

public class BudgetControllerTest {
    private Connection connection;
    private Ledger testLedger;
    private List<LedgerCategory> testCategories;

    private BudgetDAO budgetDAO;

    private BudgetController budgetController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);
        AccountDAO accountDAO = new AccountDAO(connection);
        TransactionDAO transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);

        UserController userController = new UserController(userDAO);
        budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);

        userController.register("test user", "password");
        User testUser =userController.login("test user", "password");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

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

    //edit monthly ledger-level budget
    @Test
    public void testEditBudget_Success() {
        Budget budget = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO));

        boolean result = budgetController.editBudget(budget, BigDecimal.valueOf(600.00));
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(600.00)));

        Budget updatedBudget= budgetDAO.getById(budget.getId()); //fetch from DB to verify
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(600.00)));
    }

    //edit monthly category-level budget
    @Test
    public void testEditBudget_WithCategory_Success() {
        LedgerCategory entertainment = testCategories.stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        assertNotNull(entertainment);
        Budget budget = budgetDAO.getBudgetByCategoryId(entertainment.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO));

        boolean result = budgetController.editBudget(budget, BigDecimal.valueOf(450.00));
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(450.00)));

        Budget updatedBudget= budgetDAO.getById(budget.getId()); //fetch from DB to verify
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(450.00)));
    }

    //merge category-level budget to uncategorized budget
    @Test
    public void testMergeBudgets_Success() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        LedgerCategory lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);

        Budget budget1 = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget1);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        Budget budget2 = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget2);
        budgetController.editBudget(budget2, BigDecimal.valueOf(300.00));

        Budget budget3 = budgetDAO.getBudgetByCategoryId(lunch.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget3);
        budgetController.editBudget(budget3, BigDecimal.valueOf(100.00));

        boolean result = budgetController.mergeBudgets(budget2);
        assertTrue(result);
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.valueOf(500.00))); //300+200=500

        Budget updatedBudget=budgetDAO.getById(budget2.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(500.00)));
    }

    @Test
    public void testMergeBudgets_DifferentPeriods() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        Budget budget1 = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget1);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        Budget ledgerBudget= budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.YEARLY);
        assertNotNull(ledgerBudget);
        budgetController.editBudget(ledgerBudget, BigDecimal.valueOf(100.00));

        boolean result = budgetController.mergeBudgets(ledgerBudget);
        assertTrue(result);
        assertEquals(0, ledgerBudget.getAmount().compareTo(BigDecimal.valueOf(100.00))); //amount should remain unchanged

        Budget updatedBudget=budgetDAO.getById(ledgerBudget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(100)));
    }

    //merge subcategory-level budget to category-level budget
    @Test
    public void testMergeBudgets_WithCategory_Success() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        Budget budget1 = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget1);
        budgetController.editBudget(budget1, BigDecimal.valueOf(200.00));

        LedgerCategory dinner = testCategories.stream()
                .filter(c -> c.getName().equals("Dinner"))
                .findFirst()
                .orElse(null);
        assertNotNull(dinner);
        Budget budget2 = budgetDAO.getBudgetByCategoryId(dinner.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget2);
        budgetController.editBudget(budget2, BigDecimal.valueOf(150.00));

        boolean result = budgetController.mergeBudgets(budget1);
        assertTrue(result);
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.valueOf(350.00)));

        Budget updatedBudget=budgetDAO.getById(budget1.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(350.00)));
    }

    //merge expired budgets: monthly category-level budget and monthly ledger-level budget
    @Test
    public void testMergeBudgets_ExpiredBudgets_Case1() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        Budget budget1 = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget1);
        budget1.setAmount(BigDecimal.valueOf(200.00));
        //simulate expired budget by setting start and end date in the past
        budget1.setStartDate(LocalDate.of(2025, 1, 1));
        budget1.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(budget1);

        Budget ledgerBudget= budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(ledgerBudget);
        ledgerBudget.setAmount(BigDecimal.valueOf(300.00));
        //simulate expired budget by setting start and end date in the past
        ledgerBudget.setStartDate(LocalDate.of(2025, 1, 1));
        ledgerBudget.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(ledgerBudget);

        boolean result = budgetController.mergeBudgets(ledgerBudget);
        assertTrue(result);

        Budget updatedLedgerBudget=budgetDAO.getById(ledgerBudget.getId());
        assertEquals(0, updatedLedgerBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedLedgerBudget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedLedgerBudget.getEndDate());

        Budget updatedCategoryBudget=budgetDAO.getById(budget1.getId());
        assertEquals(0, updatedCategoryBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedCategoryBudget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedCategoryBudget.getEndDate());
    }


    @Test
    public void testMergeBudgets_ExpiredBudgets_Case2() {
        LedgerCategory food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        Budget budget1 = budgetDAO.getBudgetByCategoryId(food.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget1);
        budget1.setAmount(BigDecimal.valueOf(200.00));
        budget1.setStartDate(LocalDate.of(2025, 1, 1));
        budget1.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(budget1);

        LedgerCategory lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);
        Budget lunchBudget= budgetDAO.getBudgetByCategoryId(lunch.getId(), Budget.Period.MONTHLY);
        assertNotNull(lunchBudget);
        lunchBudget.setAmount(BigDecimal.valueOf(300.00));
        lunchBudget.setStartDate(LocalDate.of(2025, 1, 1));
        lunchBudget.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(lunchBudget);

        boolean result = budgetController.mergeBudgets(budget1);
        assertTrue(result);

        Budget updatedCategoryBudget=budgetDAO.getById(budget1.getId());
        assertEquals(0, updatedCategoryBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedCategoryBudget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedCategoryBudget.getEndDate());

        Budget updatedSubcategoryBudget=budgetDAO.getById(lunchBudget.getId());
        assertEquals(0, updatedSubcategoryBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedSubcategoryBudget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedSubcategoryBudget.getEndDate());
    }
    //refresh budget test
    @Test
    public void testRefreshBudget_ExpiredBudget() {
        Budget budget = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(500.00));
        //simulate expired budget by setting start and end date in the past
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        budgetDAO.update(budget);

        boolean result = budgetController.refreshBudget(budget);
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.ZERO)); //should be reset to 0 after refresh
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), budget.getStartDate()); //should be refreshed to current month
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), budget.getEndDate()); //should be refreshed to current month

        Budget updatedBudget= budgetDAO.getById(budget.getId()); //fetch from DB to verify
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedBudget.getStartDate());
        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedBudget.getEndDate());
    }

    @Test
    public void testRefreshBudget_ActiveBudget() {
        Budget budget = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        budgetController.editBudget(budget, BigDecimal.valueOf(500.00));
        LocalDate originalStartDate = budget.getStartDate();
        LocalDate originalEndDate = budget.getEndDate();

        boolean result = budgetController.refreshBudget(budget);
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(500.00))); //amount should remain unchanged
        assertEquals(originalStartDate, budget.getStartDate()); //start date should remain unchanged
        assertEquals(originalEndDate, budget.getEndDate()); //end date should remain unchanged
    }

    //test getActiveBudgetsByLedger
    @Test
    public void testGetActiveBudgetsByLedger() {
        //get monthly budget for ledger
        Budget budget1 = budgetController.getActiveBudgetByLedger(testLedger, Budget.Period.MONTHLY);
        assertNotNull(budget1);
        assertEquals(Budget.Period.MONTHLY, budget1.getPeriod());
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));

        //get yearly budget for ledger
        Budget budget2 = budgetController.getActiveBudgetByLedger(testLedger, Budget.Period.YEARLY);
        assertNotNull(budget2);
        assertEquals(Budget.Period.YEARLY, budget2.getPeriod());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByLedger if budgets are expired
    @Test
    public void testGetActiveBudgetsByLedger_ExpiredBudgets() {
        Budget budget = budgetDAO.getBudgetByLedgerId(testLedger.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);
        //set start and end date to past to simulate expired budget
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        //set amount to non-zero
        budget.setAmount(BigDecimal.valueOf(500.00));
        budgetDAO.update(budget); //persist changes

        Budget activeBudget = budgetController.getActiveBudgetByLedger(testLedger, Budget.Period.MONTHLY);
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

        Budget activeBudget2 = budgetController.getActiveBudgetByLedger(testLedger, Budget.Period.YEARLY);
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
        Budget budget1 = budgetController.getActiveBudgetByCategory(food, Budget.Period.MONTHLY);
        assertNotNull(budget1);
        assertEquals(Budget.Period.MONTHLY, budget1.getPeriod());
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));

        //get yearly budget for food category
        Budget budget2 = budgetController.getActiveBudgetByCategory(food, Budget.Period.YEARLY);
        assertNotNull(budget2);
        assertEquals(Budget.Period.YEARLY, budget2.getPeriod());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory (first level) if budgets are expired
    @Test
    public void testGetActiveBudgetsByCategory_ExpiredBudgets() {
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

        Budget activeBudget = budgetController.getActiveBudgetByCategory(food, Budget.Period.MONTHLY);
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

        Budget activeBudget2 = budgetController.getActiveBudgetByCategory(food, Budget.Period.YEARLY);
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
        Budget budget1 = budgetController.getActiveBudgetByCategory(lunch, Budget.Period.MONTHLY);
        assertNotNull(budget1);
        assertEquals(Budget.Period.MONTHLY, budget1.getPeriod());
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));

        //get yearly budget for lunch category
        Budget budget2 = budgetController.getActiveBudgetByCategory(lunch, Budget.Period.YEARLY);
        assertNotNull(budget2);
        assertEquals(Budget.Period.YEARLY, budget2.getPeriod());
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory (second level) if budgets are expired
    @Test
    public void testGetActiveBudgetsByCategory_SecondLevel_ExpiredBudgets() {
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

        Budget activeBudget = budgetController.getActiveBudgetByCategory(lunch, Budget.Period.MONTHLY);
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
        Budget activeBudget2 = budgetController.getActiveBudgetByCategory(lunch, Budget.Period.YEARLY);
        assertNotNull(activeBudget2);
        assertEquals(Budget.Period.YEARLY, activeBudget2.getPeriod());
        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
    }

}

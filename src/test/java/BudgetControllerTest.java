import com.ledger.BusinessLogic.BudgetController;
import com.ledger.BusinessLogic.LedgerController;
import com.ledger.BusinessLogic.UserController;
import com.ledger.DomainModel.Budget;
import com.ledger.DomainModel.Ledger;
import com.ledger.DomainModel.LedgerCategory;
import com.ledger.DomainModel.Period;
import com.ledger.ORM.*;
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

public class BudgetControllerTest {
    private Connection connection;
    private Ledger testLedger;
    private Budget monthlyTotalBudget;
    private Budget yearlyTotalBudget;
    private LedgerCategory food;
    private LedgerCategory lunch;

    private BudgetDAO budgetDAO;

    private BudgetController budgetController;

    @BeforeEach
    public void setUp() {
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        budgetDAO = new BudgetDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        TransactionDAO transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);

        UserController userController = new UserController(userDAO);
        budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);

        userController.register("test user", "password");
        userController.login("test user", "password");

        testLedger = ledgerController.createLedger("Test Ledger");
        monthlyTotalBudget = budgetDAO.getBudgetByLedger(testLedger, Period.MONTHLY);
        yearlyTotalBudget = budgetDAO.getBudgetByLedger(testLedger, Period.YEARLY);

        List<LedgerCategory> testCategories = ledgerCategoryDAO.getTreeByLedger(testLedger);
        food = testCategories.stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        lunch = testCategories.stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
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
    public void testEditBudget_Success() {
        assertEquals(0, monthlyTotalBudget.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, yearlyTotalBudget.getAmount().compareTo(BigDecimal.ZERO));

        assertTrue(budgetController.editBudget(monthlyTotalBudget, BigDecimal.valueOf(600.00)));
        assertTrue(budgetController.editBudget(yearlyTotalBudget, BigDecimal.valueOf(7200.00)));
        Budget updatedBudget= budgetDAO.getById(monthlyTotalBudget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(600.00)));
        updatedBudget= budgetDAO.getById(yearlyTotalBudget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(7200.00)));
    }

    @Test
    public void testEditBudget_Failure(){
        assertFalse(budgetController.editBudget(null, BigDecimal.valueOf(500.00)));
        assertFalse(budgetController.editBudget(monthlyTotalBudget, null));
        assertFalse(budgetController.editBudget(monthlyTotalBudget, BigDecimal.valueOf(-100.00)));
    }


    @Test
    public void testMergeBudgets() {
        Budget foodBudget = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
        budgetController.editBudget(foodBudget, BigDecimal.valueOf(200.00)); //set food budget to 200
        Budget lunchBudget = budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY);
        budgetController.editBudget(lunchBudget, BigDecimal.valueOf(100.00)); //set lunch budget to 100
        budgetController.editBudget(monthlyTotalBudget, BigDecimal.valueOf(300.00)); //set ledger-level budget to 300
        assertTrue(budgetController.mergeBudgets(monthlyTotalBudget)); //merge category budgets into ledger budget

        Budget updatedBudget=budgetDAO.getById(monthlyTotalBudget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(500.00)));
        Budget updateBudget2=budgetDAO.getById(yearlyTotalBudget.getId());
        assertEquals(0, updateBudget2.getAmount().compareTo(BigDecimal.ZERO)); //yearly budget should remain unchanged

        assertTrue(budgetController.mergeBudgets(foodBudget)); //merge lunch budget into food budget
        updatedBudget=budgetDAO.getById(foodBudget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(300.00)));
        updateBudget2=budgetDAO.getById(yearlyTotalBudget.getId());
        assertEquals(0, updateBudget2.getAmount().compareTo(BigDecimal.ZERO)); //yearly budget should remain unchanged

        assertTrue(budgetController.mergeBudgets(monthlyTotalBudget)); //merge again food budget into ledger budget
        updatedBudget=budgetDAO.getById(monthlyTotalBudget.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(800.00)));
        updateBudget2=budgetDAO.getById(yearlyTotalBudget.getId());
        assertEquals(0, updateBudget2.getAmount().compareTo(BigDecimal.ZERO)); //yearly budget should remain unchanged
    }

    @Test
    public void testMergeBudgets_Failure(){
        Budget subcategoryBudget = budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY);
        assertFalse(budgetController.mergeBudgets(subcategoryBudget)); //cannot merge subcategory budget directly
        assertFalse(budgetController.mergeBudgets(null)); //null budget
    }

    //merge expired budgets: monthly category-level budget and monthly ledger-level budget
//    @Test
//    public void testMergeBudgets_ExpiredBudgets_Case1() {
//        Budget budget1 = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
//        budget1.setAmount(BigDecimal.valueOf(200.00));
//        //simulate expired budget by setting start and end date in the past
//        budget1.setStartDate(LocalDate.of(2025, 1, 1));
//        budget1.setEndDate(LocalDate.of(2025, 1, 31));
//        budgetDAO.update(budget1);
//
//        monthlyTotalBudget.setAmount(BigDecimal.valueOf(300.00));
//        //simulate expired budget by setting start and end date in the past
//        monthlyTotalBudget.setStartDate(LocalDate.of(2025, 1, 1));
//        monthlyTotalBudget.setEndDate(LocalDate.of(2025, 1, 31));
//        budgetDAO.update(monthlyTotalBudget);
//
//        assertTrue( budgetController.mergeBudgets(monthlyTotalBudget));
//
//        Budget updatedLedgerBudget=budgetDAO.getById(monthlyTotalBudget.getId());
//        assertEquals(0, updatedLedgerBudget.getAmount().compareTo(BigDecimal.ZERO));
//        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedLedgerBudget.getStartDate());
//        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedLedgerBudget.getEndDate());
//
//        Budget updatedCategoryBudget=budgetDAO.getById(budget1.getId());
//        assertEquals(0, updatedCategoryBudget.getAmount().compareTo(BigDecimal.ZERO));
//        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedCategoryBudget.getStartDate());
//        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedCategoryBudget.getEndDate());
//    }
//
//    @Test
//    public void testMergeBudgets_ExpiredBudgets_Case2() {
//        Budget foodBudget = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
//        foodBudget.setAmount(BigDecimal.valueOf(200.00));
//        foodBudget.setStartDate(LocalDate.of(2025, 1, 1));
//        foodBudget.setEndDate(LocalDate.of(2025, 1, 31));
//        budgetDAO.update(foodBudget);
//
//        Budget lunchBudget= budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY);
//        lunchBudget.setAmount(BigDecimal.valueOf(300.00));
//        lunchBudget.setStartDate(LocalDate.of(2025, 1, 1));
//        lunchBudget.setEndDate(LocalDate.of(2025, 1, 31));
//        budgetDAO.update(lunchBudget);
//
//        assertTrue(budgetController.mergeBudgets(foodBudget));
//
//        Budget updatedCategoryBudget=budgetDAO.getById(foodBudget.getId());
//        assertEquals(0, updatedCategoryBudget.getAmount().compareTo(BigDecimal.ZERO));
//        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedCategoryBudget.getStartDate());
//        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedCategoryBudget.getEndDate());
//
//        Budget updatedSubcategoryBudget=budgetDAO.getById(lunchBudget.getId());
//        assertEquals(0, updatedSubcategoryBudget.getAmount().compareTo(BigDecimal.ZERO));
//        assertEquals(LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()), updatedSubcategoryBudget.getStartDate());
//        assertEquals(LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()), updatedSubcategoryBudget.getEndDate());
//    }

    //test getActiveBudgetsByLedger
//    @Test
//    public void testGetActiveBudgetsByLedger() {
//        //get monthly budget for ledger
//        assertEquals(Period.MONTHLY, monthlyTotalBudget.getPeriod());
//        assertEquals(0, monthlyTotalBudget.getAmount().compareTo(BigDecimal.ZERO));
//
//        //get yearly budget for ledger
//        assertEquals(Period.YEARLY, yearlyTotalBudget.getPeriod());
//        assertEquals(0, yearlyTotalBudget.getAmount().compareTo(BigDecimal.ZERO));
//    }

    //test getActiveBudgetsByLedger if budgets are expired
    @Test
    public void testGetActiveBudgetsByLedger_ExpiredBudgets() {
        //set start and end date to past to simulate expired budget
        monthlyTotalBudget.setStartDate(LocalDate.of(2025, 1, 1));
        monthlyTotalBudget.setEndDate(LocalDate.of(2025, 1, 31));
        //set amount to non-zero
        monthlyTotalBudget.setAmount(BigDecimal.valueOf(500.00));
        budgetDAO.update(monthlyTotalBudget); //persist changes

        Budget activeBudget = budgetController.getActiveBudgetByLedger(testLedger, Period.MONTHLY);
        assertNotNull(activeBudget);
        assertEquals(Period.MONTHLY, activeBudget.getPeriod());
        assertEquals(0, activeBudget.getAmount().compareTo(BigDecimal.ZERO));

        //set start and end date to past to simulate expired budget
        yearlyTotalBudget.setStartDate(LocalDate.of(2023, 1, 1));
        yearlyTotalBudget.setEndDate(LocalDate.of(2023, 12, 31));
        //set amount to non-zero
        yearlyTotalBudget.setAmount(BigDecimal.valueOf(2000.00));
        budgetDAO.update(yearlyTotalBudget); //persist changes

        Budget activeBudget2 = budgetController.getActiveBudgetByLedger(testLedger, Period.YEARLY);
        assertNotNull(activeBudget2);
        assertEquals(Period.YEARLY, activeBudget2.getPeriod());
        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory
//    @Test
//    public void testGetActiveBudgetsByCategory() {
//        //get monthly budget for food category
//        Budget budget1 = budgetController.getActiveBudgetByCategory(food, Period.MONTHLY);
//        assertEquals(Period.MONTHLY, budget1.getPeriod());
//        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));
//
//        //get yearly budget for food category
//        Budget budget2 = budgetController.getActiveBudgetByCategory(food, Period.YEARLY);
//        assertEquals(Period.YEARLY, budget2.getPeriod());
//        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
//    }

    //test getActiveBudgetsByCategory (first level) if budgets are expired
    @Test
    public void testGetActiveBudgetsByCategory_ExpiredBudgets() {
        Budget budget = budgetDAO.getBudgetByCategory(food, Period.MONTHLY);
        //set start and end date to past to simulate expired budget
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));
        //set amount to non-zero
        budget.setAmount(BigDecimal.valueOf(300.00));
        budgetDAO.update(budget); //persist changes

        Budget activeBudget = budgetController.getActiveBudgetByCategory(food, Period.MONTHLY);
        assertEquals(Period.MONTHLY, activeBudget.getPeriod());
        assertEquals(0, activeBudget.getAmount().compareTo(BigDecimal.ZERO));

        Budget budget2 = budgetDAO.getBudgetByCategory(food, Period.YEARLY);
        assertNotNull(budget2);
        //set start and end date to past to simulate expired budget
        budget2.setStartDate(LocalDate.of(2023, 1, 1));
        budget2.setEndDate(LocalDate.of(2023, 12, 31));
        //set amount to non-zero
        budget2.setAmount(BigDecimal.valueOf(1500.00));
        budgetDAO.update(budget2); //persist changes

        Budget activeBudget2 = budgetController.getActiveBudgetByCategory(food, Period.YEARLY);
        assertEquals(Period.YEARLY, activeBudget2.getPeriod());
        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
    }

    //test getActiveBudgetsByCategory (second level)
//    @Test
//    public void testGetActiveBudgetsByCategory_SecondLevel() {
//        //get monthly budget for lunch category
//        Budget budget1 = budgetController.getActiveBudgetByCategory(lunch, Period.MONTHLY);
//        assertEquals(Period.MONTHLY, budget1.getPeriod());
//        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.ZERO));
//
//        //get yearly budget for lunch category
//        Budget budget2 = budgetController.getActiveBudgetByCategory(lunch, Period.YEARLY);
//        assertEquals(Period.YEARLY, budget2.getPeriod());
//        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.ZERO));
//    }

    //test getActiveBudgetsByCategory (second level) if budgets are expired
//    @Test
//    public void testGetActiveBudgetsByCategory_SecondLevel_ExpiredBudgets() {
//        Budget budget = budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY);
//        //set start and end date to past to simulate expired budget
//        budget.setStartDate(LocalDate.of(2025, 1, 1));
//        budget.setEndDate(LocalDate.of(2025, 1, 31));
//        //set amount to non-zero
//        budget.setAmount(BigDecimal.valueOf(150.00));
//        budgetDAO.update(budget); //persist changes
//
//        Budget activeBudget = budgetController.getActiveBudgetByCategory(lunch, Period.MONTHLY);
//        assertEquals(Period.MONTHLY, activeBudget.getPeriod());
//        assertEquals(0, activeBudget.getAmount().compareTo(BigDecimal.ZERO));
//
//        Budget budget2 = budgetDAO.getBudgetByCategory(lunch, Period.YEARLY);
//        assertNotNull(budget2);
//        //set start and end date to past to simulate expired budget
//        budget2.setStartDate(LocalDate.of(2023, 1, 1));
//        budget2.setEndDate(LocalDate.of(2023, 12, 31));
//        //set amount to non-zero
//        budget2.setAmount(BigDecimal.valueOf(800.00));
//        budgetDAO.update(budget2); //persist changes
//
//        Budget activeBudget2 = budgetController.getActiveBudgetByCategory(lunch, Period.YEARLY);
//        assertEquals(Period.YEARLY, activeBudget2.getPeriod());
//        assertEquals(0, activeBudget2.getAmount().compareTo(BigDecimal.ZERO));
//    }

}

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

        userController = new UserController(userDAO);
        budgetController = new BudgetController(budgetDAO, transactionDAO, ledgerCategoryDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
        accountController = new AccountController(accountDAO, transactionDAO);

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
                for (String s : sql.split(";")) {  // 按分号拆分
                    if (!s.trim().isEmpty()) {
                        stmt.execute(s);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute " + filePath, e);
        }
    }

    //create budget for ledger without category
    @Test
    public void testCreateBudget_Success() throws SQLException {
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget);
    }

    @Test
    public void testCreateBudget_DuplicatePeriod_Failure() throws SQLException {
        Budget budget1 = budgetController.createBudget(BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget1);

        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(600.00), null, Budget.Period.MONTHLY, testLedger);
        assertNull(budget2); //should fail due to duplicate budget
    }

    @Test
    public void testCreateBudget_WithCategory_Success() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(300.00), food, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget);
    }

    @Test
    public void testCreateBudget_WithSubCategory_Success() throws SQLException {
        LedgerCategory dinner = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Dinner"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(150.00), dinner, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget);
    }

    @Test
    public void testCreateBudget_DuplicateCategory_Failure() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = budgetController.createBudget( BigDecimal.valueOf(300.00), food, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget1);

        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(400.00), food, Budget.Period.MONTHLY, testLedger);
        assertNull(budget2); //should fail due to duplicate budget for same category
    }

    @Test
    public void testCreateBudget_MoreBudgets_SamePeriod_Success() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = budgetController.createBudget( BigDecimal.valueOf(300.00), food, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget1);

        LedgerCategory transport = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(400.00), transport, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget2); //should succeed as different categories

        Budget budget3 = budgetController.createBudget(BigDecimal.valueOf(200.00), null, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget3); //should fail as ledger level budget for same period exists

        LedgerCategory lunch = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        Budget budget4 = budgetController.createBudget(BigDecimal.valueOf(150.00), lunch, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget4); //should succeed as different sub-category
    }

    @Test
    public void testDeleteBudget_Success() throws SQLException {
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget);

        boolean result = budgetController.deleteBudget(budget);
        assertTrue(result);
        assertNull(budgetDAO.getById(budget.getId()));
    }

    @Test
    public void testDeleteBudget_WithCategory_Success() throws SQLException {
        LedgerCategory transport = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(200.00), transport, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget);

        boolean result = budgetController.deleteBudget(budget);
        assertTrue(result);
        assertNull(budgetDAO.getById(budget.getId()));
    }

    @Test
    public void testEditBudget_Success() throws SQLException {
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY, testLedger);

        boolean result = budgetController.editBudget(budget, BigDecimal.valueOf(600.00));
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(600.00)));
    }

    @Test
    public void testEditBudget_WithCategory_Success() throws SQLException {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(400.00), entertainment, Budget.Period.MONTHLY, testLedger);

        boolean result = budgetController.editBudget(budget, BigDecimal.valueOf(450.00));
        assertTrue(result);
        assertEquals(0, budget.getAmount().compareTo(BigDecimal.valueOf(450.00)));
    }

    //merge category of first level to uncategorized budget
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
        Budget budget1 = budgetController.createBudget(BigDecimal.valueOf(200.00), food, Budget.Period.MONTHLY, testLedger);
        assertNotNull(budget1);
        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(300.00), null, Budget.Period.MONTHLY, testLedger);
        Budget budget3 = budgetController.createBudget(BigDecimal.valueOf(100.00), lunch, Budget.Period.MONTHLY, testLedger); //budget under sub-category
        assertNotNull(budget3);


        boolean result = budgetController.mergeBudgets(budget2);
        assertTrue(result);
        assertEquals(0, budget2.getAmount().compareTo(BigDecimal.valueOf(500.00)));

        Budget updatedBudget=budgetDAO.getById(budget2.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(500.00)));
    }

    //merge category of second level to first level budget
    @Test
    public void testMergeBudgets_WithCategory_Success() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = budgetController.createBudget(BigDecimal.valueOf(200.00), food, Budget.Period.MONTHLY, testLedger);

        LedgerCategory dinner = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Dinner"))
                .findFirst()
                .orElse(null);
        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(150.00), dinner, Budget.Period.MONTHLY, testLedger);

        boolean result = budgetController.mergeBudgets(budget1);
        assertTrue(result);
        assertEquals(0, budget1.getAmount().compareTo(BigDecimal.valueOf(350.00)));
    }

    //test is_over_budget
    @Test
    public void testIsOverBudget() {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(200.00), food, Budget.Period.MONTHLY, testLedger);

        LedgerCategory lunch = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);

        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(400.00), null, Budget.Period.MONTHLY, testLedger);

        //add transactions to exceed budget of food category
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));
        transactionController.createExpense(testLedger, testAccount, lunch, "Grocery shopping", LocalDate.now(), BigDecimal.valueOf(150.00));

        boolean isOverBudget = budgetController.isOverBudget(budget, testLedger);
        assertTrue(isOverBudget);

        //not exceed budget of ledger
        assertFalse(budgetController.isOverBudget(budget2, testLedger));

    }

    @Test
    public void testIsOverBudget_NoTransactions_False() {
        LedgerCategory transport = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(300.00), transport, Budget.Period.MONTHLY, testLedger);
        Budget budget2 = budgetController.createBudget(BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY, testLedger);

        boolean isOverBudget = budgetController.isOverBudget(budget, testLedger);
        assertFalse(isOverBudget);
        assertFalse(budgetController.isOverBudget(budget2, testLedger));
    }

    @Test
    public void testIsOverBudget_InactiveBudget_False() {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(400.00), entertainment, Budget.Period.MONTHLY, testLedger);

        //simulate inactive budget by setting start and end date in the past
        budget.setStartDate(LocalDate.of(2025, 1, 1));
        budget.setEndDate(LocalDate.of(2025, 1, 31));

        boolean isOverBudget = budgetController.isOverBudget(budget, testLedger);
        assertFalse(isOverBudget); //should be false as budget is inactive
    }

    @Test
    public void testIsOverBudget_OverPeriod_False() {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(400.00), entertainment, Budget.Period.MONTHLY, testLedger);

        transactionController.createExpense(testLedger, testAccount, entertainment, "Movie", LocalDate.of(2025, 1, 1), BigDecimal.valueOf(500.00));

        boolean isOverBudget = budgetController.isOverBudget(budget, testLedger);
        assertFalse(isOverBudget); //should be false as budget period is over
    }

    @Test
    public void testIsOverBudget_BoundaryCase() {
        Budget budget1 = budgetController.createBudget(BigDecimal.valueOf(300.00), null, Budget.Period.MONTHLY, testLedger);

        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(BigDecimal.valueOf(120.00), food, Budget.Period.MONTHLY, testLedger);

        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, food, "Grocery shopping", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(21.00));

        boolean isOverBudget = budgetController.isOverBudget(budget, testLedger);
        assertTrue(isOverBudget);
        assertFalse(budgetController.isOverBudget(budget1, testLedger));

        LedgerCategory transport = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Transport"))
                .findFirst()
                .orElse(null);
        transactionController.createExpense(testLedger, testAccount, transport, "Bus ticket", LocalDate.now().withDayOfMonth(1), BigDecimal.valueOf(100.00));
        transactionController.createExpense(testLedger, testAccount, transport, "Taxi", LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()), BigDecimal.valueOf(201.00));
        assertTrue(budgetController.isOverBudget(budget1, testLedger));
    }

}

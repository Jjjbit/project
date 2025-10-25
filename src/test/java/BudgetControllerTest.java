import com.ledger.business.BudgetController;
import com.ledger.business.LedgerController;
import com.ledger.business.UserController;
import com.ledger.domain.Budget;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;
import com.ledger.domain.User;
import com.ledger.orm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;

    private UserDAO userDAO;
    private BudgetDAO budgetDAO;
    private LedgerDAO ledgerDAO;
    private TransactionDAO transactionDAO;
    private UserController userController;
    private CategoryDAO categoryDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private AccountDAO accountDAO;
    private BudgetController budgetController;
    private LedgerController ledgerController;
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
        budgetController = new BudgetController(budgetDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO);

        userController.register("testuser", "password");
        testUser =userController.login("testuser", "password");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

    }

    @AfterEach
    public void tearDown() throws SQLException {
        readResetScript();
    }

    private void runSchemaScript() {
        try {
            Path path = Paths.get("src/test/resources/schema.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema.sql", e);
        }
    }

    private void readResetScript() throws SQLException {
        try {
            Path path = Paths.get("src/test/resources/reset.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read reset.sql", e);
        }
    }

    @Test
    public void testCreateBudget_Success() throws SQLException {
        Budget budget = budgetController.createBudget(testUser, BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY);
        assertNotNull(budget);
    }

    @Test
    public void testCreateBudget_DuplicatePeriod_Failure() throws SQLException {
        Budget budget1 = budgetController.createBudget(testUser, BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY);
        assertNotNull(budget1);

        Budget budget2 = budgetController.createBudget(testUser, BigDecimal.valueOf(600.00), null, Budget.Period.MONTHLY);
        assertNull(budget2); //should fail due to duplicate budget
    }
    @Test
    public void testCreateBudget_WithCategory_Success() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget = budgetController.createBudget(testUser, BigDecimal.valueOf(300.00), food, Budget.Period.MONTHLY);
        assertNotNull(budget);
        assertEquals(1, testUser.getBudgets().size());
        assertEquals(food.getId(), budget.getCategory().getId());
    }

    @Test
    public void testDeleteBudget_Success() throws SQLException {
        Budget budget = budgetController.createBudget(testUser, BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY);
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
        Budget budget = budgetController.createBudget(testUser, BigDecimal.valueOf(200.00), transport, Budget.Period.MONTHLY);
        assertNotNull(budget);

        boolean result = budgetController.deleteBudget(budget);
        assertTrue(result);
        assertEquals(0, testUser.getBudgets().size());
        assertNull(budgetDAO.getById(budget.getId()));
    }

    @Test
    public void testEditBudget_Success() throws SQLException {
        Budget budget = budgetController.createBudget(testUser, BigDecimal.valueOf(500.00), null, Budget.Period.MONTHLY);

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
        Budget budget = budgetController.createBudget(testUser, BigDecimal.valueOf(400.00), entertainment, Budget.Period.MONTHLY);

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
        Budget budget1 = budgetController.createBudget(testUser, BigDecimal.valueOf(200.00), food, Budget.Period.MONTHLY);
        Budget budget2 = budgetController.createBudget(testUser, BigDecimal.valueOf(300.00), null, Budget.Period.MONTHLY);

        boolean result = budgetController.mergeBudgets(budget2);
        assertTrue(result);
        assertEquals(BigDecimal.valueOf(500.00), budget2.getAmount());
        assertEquals(2, testUser.getBudgets().size());

        Budget updatedBudget=budgetDAO.getById(budget2.getId());
        assertEquals(0, updatedBudget.getAmount().compareTo(BigDecimal.valueOf(500.00)));
    }

    //merge category of second level to first level budget
    @Test
    public void testMergeBudgets_WithCategory_Success() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        Budget budget1 = budgetController.createBudget(testUser, BigDecimal.valueOf(200.00), food, Budget.Period.MONTHLY);
        LedgerCategory dinner = testLedger.getCategories().stream()
                .filter(c -> c.getName().equals("Dinner"))
                .findFirst()
                .orElse(null);
        Budget budget2 = budgetController.createBudget(testUser, BigDecimal.valueOf(150.00), dinner, Budget.Period.MONTHLY);

        boolean result = budgetController.mergeBudgets(budget1);
        assertTrue(result);
        assertEquals(BigDecimal.valueOf(350.00), budget1.getAmount());
        assertEquals(2, testUser.getBudgets().size());
    }

}

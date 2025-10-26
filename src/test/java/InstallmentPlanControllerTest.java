import com.ledger.business.AccountController;
import com.ledger.business.InstallmentPlanController;
import com.ledger.business.LedgerController;
import com.ledger.business.UserController;
import com.ledger.domain.*;
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
import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class InstallmentPlanControllerTest {
    private Connection connection;
    private User testUser;
    private CreditAccount account;
    private LedgerCategory category;
    private Ledger testLedger;

    private UserDAO userDAO;
    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private InstallmentPlanDAO installmentPlanDAO;
    private LedgerDAO ledgerDAO;
    private CategoryDAO categoryDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private UserController userController;
    private InstallmentPlanController installmentPlanController;
    private AccountController accountController;
    private LedgerController ledgerController;
    @BeforeEach
    public void setUp() throws SQLException {
        connection = com.ledger.orm.ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        installmentPlanDAO = new InstallmentPlanDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        categoryDAO = new CategoryDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);

        userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, userDAO, transactionDAO);
        installmentPlanController = new InstallmentPlanController(installmentPlanDAO, transactionDAO, accountDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO);

        // Create a test user
        userController.register("testuser", "password123");
        testUser = userController.login("testuser", "password123");

        // Create a test credit account for the user
        account = accountController.createCreditAccount(
                "Test Credit Account",
                "Credit Account Note",
                BigDecimal.valueOf(1000.00), // initial balance
                true,
                true,
                testUser,
                AccountType.CREDIT_CARD,
                BigDecimal.valueOf(1000), // credit limit
                BigDecimal.valueOf(20), // current debt
                10, 25
        );

        // Create a test ledger for the user
        testLedger = ledgerController.createLedger("Test Ledger", testUser);

        // get a test ledger category
        category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
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

    //create
    @Test
    public void testCreateInstallmentPlan_Success() throws SQLException {
        InstallmentPlan plan = installmentPlanController.createInstallmentPlan(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                0,
                BigDecimal.valueOf(5.00), // fee rate
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                LocalDate.now(),
                category
        );
        assertNotNull(plan);
        assertEquals(1, account.getInstallmentPlans().size());
        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(146.00)));

        assertNotNull(installmentPlanDAO.getById(plan.getId()));
        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(146.00)));

    }
    //delete
    @Test
    public void testDeleteInstallmentPlan_Success() throws SQLException {
        InstallmentPlan plan = installmentPlanController.createInstallmentPlan(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                0,
                BigDecimal.valueOf(5.00), // fee rate
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                LocalDate.now(),
                category
        );

        boolean deleted = installmentPlanController.deleteInstallmentPlan(plan);
        assertTrue(deleted);
        assertEquals(0, account.getInstallmentPlans().size());
        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00)));

        assertNull(installmentPlanDAO.getById(plan.getId()));
        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00)));
    }

    //edit
    @Test
    public void testEditInstallmentPlan_Success() throws SQLException {
        InstallmentPlan plan = installmentPlanController.createInstallmentPlan(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                0,
                BigDecimal.valueOf(5.00), // fee rate
                InstallmentPlan.FeeStrategy.EVENLY_SPLIT,
                LocalDate.now(),
                category
        );
        LedgerCategory newCategory = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Electronics"))
                .findFirst()
                .orElse(null);


        boolean updated = installmentPlanController.editInstallmentPlan(plan,
                BigDecimal.valueOf(150.00),
                6,
                1,
                BigDecimal.valueOf(4.00),
                InstallmentPlan.FeeStrategy.UPFRONT,
                "new Name",
                null);
        assertTrue(updated);
        assertEquals(0, plan.getTotalAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(1, plan.getPaidPeriods());
        assertEquals(0, plan.getFeeRate().compareTo(BigDecimal.valueOf(4.00)));
        assertEquals(InstallmentPlan.FeeStrategy.UPFRONT, plan.getFeeStrategy());
        assertEquals("new Name", plan.getName());
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.valueOf(125.00))); //
        assertEquals(0, account.getCurrentDebt().compareTo(BigDecimal.valueOf(145.00)));

        InstallmentPlan updatedPlan = installmentPlanDAO.getById(plan.getId());
        assertEquals("new Name", updatedPlan.getName());
        assertEquals(6, updatedPlan.getTotalPeriods());
        assertEquals(1, updatedPlan.getPaidPeriods());
        assertEquals(0, updatedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(0, updatedPlan.getFeeRate().compareTo(BigDecimal.valueOf(4.00)));
        assertEquals(InstallmentPlan.FeeStrategy.UPFRONT, updatedPlan.getFeeStrategy());
        assertEquals(0, updatedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(125.00)));

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(145.00)));
    }
}

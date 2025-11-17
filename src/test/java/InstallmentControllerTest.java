import com.ledger.business.AccountController;
import com.ledger.business.InstallmentController;
import com.ledger.business.LedgerController;
import com.ledger.business.UserController;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class InstallmentControllerTest {
    private Connection connection;

    private CreditAccount account;
    private LedgerCategory category;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private InstallmentDAO installmentDAO;

    private InstallmentController installmentController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = com.ledger.orm.ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        UserDAO userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        installmentDAO = new InstallmentDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        AccountController accountController = new AccountController(accountDAO, transactionDAO);
        installmentController = new InstallmentController(installmentDAO, transactionDAO, accountDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);

        // Create a test user
        userController.register("test user", "password123");
        User testUser = userController.login("test user", "password123");

        // Create a test credit account for the user
        account = accountController.createCreditAccount("Test Credit Account", "Credit Account Note",
                BigDecimal.valueOf(1000.00), // initial balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(1000), // credit limit
                BigDecimal.valueOf(20), // current debt
                10, 25);

        // Create a test ledger for the user
        Ledger testLedger = ledgerController.createLedger("Test Ledger", testUser);

        // get a test ledger category
        category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
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

    //create
    @Test
    public void testCreateInstallment_IncludedInCurrentDebt() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(),
                category, true);
        assertNotNull(plan);
        assertEquals(1, account.getInstallmentPlans().size());
        assertNotNull(installmentDAO.getById(plan.getId()));

        Installment savedPlan = installmentDAO.getById(plan.getId());
        assertNotNull(savedPlan);
        assertEquals(0, savedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(115.50))); // 126-10.5=115.5
        assertEquals(0, savedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(120.00)));
        assertEquals(12, savedPlan.getTotalPeriods());
        assertEquals(1, savedPlan.getPaidPeriods());
        assertEquals(0, savedPlan.getInterest().compareTo(BigDecimal.valueOf(5.00)));
        assertEquals(Installment.Strategy.EVENLY_SPLIT, savedPlan.getStrategy());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        //balance remains the same
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(989.5))); //1000-10.5=989.5
        //current debt increased by 126
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(135.50))); //20+115.5=135.5

        assertEquals(1, installmentDAO.getByAccountId(account.getId()).size());
        assertEquals(savedPlan.getId(), installmentDAO.getByAccountId(account.getId()).getFirst().getId());
        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());
    }

    @Test
    public void testCreateInstallment_NotIncludedInCurrentDebt() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // fee rate
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(),
                category, false);
        assertNotNull(plan);
        assertEquals(1, account.getInstallmentPlans().size());
        assertNotNull(installmentDAO.getById(plan.getId()));

        Installment savedPlan = installmentDAO.getById(plan.getId());
        assertNotNull(savedPlan);
        assertEquals(0, savedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(115.50))); // 126-10.5=115.5
        assertEquals(0, savedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(120.00)));
        assertEquals(12, savedPlan.getTotalPeriods());
        assertEquals(1, savedPlan.getPaidPeriods());
        assertEquals(0, savedPlan.getInterest().compareTo(BigDecimal.valueOf(5.00)));
        assertEquals(Installment.Strategy.EVENLY_SPLIT, savedPlan.getStrategy());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        //balance remains the same
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(989.5))); //1000-10.5=989.5
        //current debt remains the same
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00)));

        assertEquals(1, installmentDAO.getByAccountId(account.getId()).size());
        assertEquals(savedPlan.getId(), installmentDAO.getByAccountId(account.getId()).getFirst().getId());
        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());
    }

    //test repaymentStartDate in past
    @Test
    public void testCreateInstallment_WithPastRepaymentStartDate() throws SQLException {
        LocalDate startDate = LocalDate.now().minusMonths(3); // 3 months ago
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                startDate,
                category, true);
        // total repayment = 120 + 5% = 126
        // remaining amount = 126 - (126/12 * 3) = 94.5
        // repaid amount = 126/12 *3 = 31.5

        assertNotNull(plan);
        Installment savedPlan = installmentDAO.getById(plan.getId());
        assertNotNull(savedPlan);
        assertEquals(3, savedPlan.getPaidPeriods());
        assertEquals(0, savedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(94.50)));
        assertEquals(0, savedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(120.00)));
        assertEquals(12, savedPlan.getTotalPeriods());
        assertEquals(0, savedPlan.getInterest().compareTo(BigDecimal.valueOf(5.00)));
        assertEquals(Installment.Strategy.EVENLY_SPLIT, savedPlan.getStrategy());

        assertEquals(1, installmentDAO.getByAccountId(account.getId()).size());
        assertEquals(savedPlan.getId(), installmentDAO.getByAccountId(account.getId()).getFirst().getId());
        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(category.getId()).size());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(968.50))); //1000-31.5=968.5
        //current debt increased by remaining amount 94.5
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(114.50))); //20+94.5=114.5

        assertEquals(3, plan.getPaidPeriods());
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.valueOf(94.50)));
        assertEquals(1, account.getInstallmentPlans().size());
        assertEquals(1, account.getTransactions().size());
        //balance of account 1000-31.5=968.5
        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(968.50)));
    }


    //test repaymentStartDate in future
    @Test
    public void testCreateInstallment_WithFutureRepaymentStartDate() throws SQLException {
        LocalDate startDate = LocalDate.now().plusMonths(2); // 2 months later
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                startDate,
                category, true);
        assertNotNull(plan);
        assertEquals(0, plan.getPaidPeriods());

        Installment savedPlan = installmentDAO.getById(plan.getId());
        assertNotNull(savedPlan);
        assertEquals(0, savedPlan.getPaidPeriods());
        assertEquals(0, savedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(126.00)));
        assertEquals(0, savedPlan.getTotalAmount().compareTo(BigDecimal.valueOf(120.00)));
        assertEquals(12, savedPlan.getTotalPeriods());
        assertEquals(0, savedPlan.getInterest().compareTo(BigDecimal.valueOf(5.00)));
        assertEquals(Installment.Strategy.EVENLY_SPLIT, savedPlan.getStrategy());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        //balance remains the same
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        //current debt increased by 126
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(146.00))); //20+126=146

        assertEquals(1, installmentDAO.getByAccountId(account.getId()).size());
        assertEquals(savedPlan.getId(), installmentDAO.getByAccountId(account.getId()).getFirst().getId());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
    }

    //delete
    @Test
    public void testDeleteInstallment_Success() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), //repaid periods =0
                category, true);

        boolean deleted = installmentController.deleteInstallment(plan);
        assertTrue(deleted);
        assertEquals(0, account.getInstallmentPlans().size());
        assertNull(installmentDAO.getById(plan.getId()));
        assertEquals(0, installmentDAO.getByAccountId(account.getId()).size());
    }

    @Test
    public void testDeleteInstallment_WithPaidPeriods_Success() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now().minusMonths(3), // 3 months ago
                category, true);
        installmentController.payInstallment(plan); //pay once
        //total repayment = 120 + 5% = 126
        //remaining amount = 126 - (126/12 * 4) = 84.0
        //repaid amount = 126/12 *4 =42.0

        boolean deleted = installmentController.deleteInstallment(plan);
        assertTrue(deleted);
        assertEquals(0, account.getInstallmentPlans().size());
        assertNull(installmentDAO.getById(plan.getId()));
        assertEquals(0, installmentDAO.getByAccountId(account.getId()).size());
        assertEquals(2, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(2, transactionDAO.getByCategoryId(category.getId()).size());
        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(958.00))); //balance of account remains unchanged
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00))); //current debt decreased by remaining amount 84.0
    }

    //test pay installment
    @Test
    public void testPayInstallment_Success() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(),
                category, true);

        boolean paid = installmentController.payInstallment(plan);
        assertTrue(paid);
        assertEquals(2, plan.getPaidPeriods());
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.valueOf(105.00))); // 126 - 10.5*2 = 105

        Installment updatedPlan = installmentDAO.getById(plan.getId());
        assertEquals(2, updatedPlan.getPaidPeriods());
        assertEquals(0, updatedPlan.getRemainingAmount().compareTo(BigDecimal.valueOf(105.00)));

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(979.00))); // 1000 -10.5*2=979
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(125.00))); // 20 +105.00=125

        assertEquals(2, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(2, transactionDAO.getByCategoryId(category.getId()).size());
    }

    @Test
    public void testPayInstallment_FullFlow() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(),
                category, true);

        for (int i = 2; i <= 12; i++) {
            boolean paid = installmentController.payInstallment(plan);
            assertTrue(paid);
            assertEquals(i, plan.getPaidPeriods());
        }

        Installment updatedPlan = installmentDAO.getById(plan.getId());
        assertEquals(12, updatedPlan.getPaidPeriods());
        assertEquals(0, updatedPlan.getRemainingAmount().compareTo(BigDecimal.ZERO));

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(874.00))); // 1000-126=874.00
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00))); // all installment repaid, current debt back to original 20.00

        assertEquals(12, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(12, transactionDAO.getByCategoryId(category.getId()).size());
    }

    //test pay installment when all periods paid
    @Test
    public void testPayInstallment_AllPeriodsPaid_Failure() throws SQLException {
        Installment plan = installmentController.createInstallment(
                account,
                "Test Installment Plan",
                BigDecimal.valueOf(120.00),
                12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now().minusMonths(12), // 12 months ago
                category, true);
        //all periods already paid
        //total repayment = 120 + 5% = 126
        //remaining amount = 0
        //repaid amount =126

        boolean paid = installmentController.payInstallment(plan);
        assertFalse(paid);
        assertEquals(12, plan.getPaidPeriods());
        assertEquals(0, plan.getRemainingAmount().compareTo(BigDecimal.ZERO));

        Installment updatedPlan = installmentDAO.getById(plan.getId());
        assertEquals(12, updatedPlan.getPaidPeriods());
        assertEquals(0, updatedPlan.getRemainingAmount().compareTo(BigDecimal.ZERO));

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(874.00))); // 1000-126=874.00
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00))); // all installment repaid, current debt back to original 20.00

        assertEquals(1, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(category.getId()).size());
    }
}

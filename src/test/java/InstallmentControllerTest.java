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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class InstallmentControllerTest {
    private Connection connection;

    private CreditAccount account;
    private LedgerCategory category;
    private Ledger testLedger;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private InstallmentDAO installmentDAO;

    private InstallmentController installmentController;
    private AccountController accountController;
    private User testUser;
    private List<LedgerCategory> testCategories;

    @BeforeEach
    public void setUp() {
        connection = com.ledger.orm.ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        accountDAO = new AccountDAO(connection);
        installmentDAO = new InstallmentDAO(connection, ledgerCategoryDAO);
        transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);
        DebtPaymentDAO debtPaymentDAO = new DebtPaymentDAO(connection, transactionDAO);
        InstallmentPaymentDAO installmentPaymentDAO = new InstallmentPaymentDAO(connection, transactionDAO, installmentDAO);

        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO);
        installmentController = new InstallmentController(installmentDAO, transactionDAO, accountDAO, installmentPaymentDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);

        // Create a test user
        userController.register("test user", "password123");
        testUser = userController.login("test user", "password123");

        // Create a test credit account for the user
        account = accountController.createCreditAccount("Test Credit Account", "Credit Account Note",
                BigDecimal.valueOf(1000.00), // initial balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(1000), // credit limit
                BigDecimal.valueOf(20), // current debt
                10, 25);

        // Create a test ledger for the user
        testLedger = ledgerController.createLedger("Test Ledger", testUser);

        testCategories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());

        // get a test ledger category
        category = testCategories.stream()
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

    //create
    @Test
    public void testCreateInstallment_IncludedInCurrentDebt() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, LocalDate.now(), //repaid periods =1
                category, true, testLedger);
        assertNotNull(plan);
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
        assertEquals(1, transactionDAO.getByCategoryId(category.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
    }

    @Test
    public void testCreateInstallment_NotIncludedInCurrentDebt() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, LocalDate.now(), category, false, testLedger);
        assertNotNull(plan);
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
    public void testCreateInstallment_WithPastRepaymentStartDate() {
        LocalDate startDate = LocalDate.now().minusMonths(3); // 3 months ago
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, startDate, category, true, testLedger);
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
        //balance of account 1000-31.5=968.5
        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(968.50)));
    }


    //test repaymentStartDate in future
    @Test
    public void testCreateInstallment_WithFutureRepaymentStartDate() {
        LocalDate startDate = LocalDate.now().plusMonths(2); // 2 months later
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, startDate, category, true, testLedger);
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
    public void testDeleteInstallment_Success() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), //repaid periods =0
                category, true, testLedger);

        boolean deleted = installmentController.deleteInstallment(plan, account);
        assertTrue(deleted);
        assertNull(installmentDAO.getById(plan.getId()));
        assertEquals(0, installmentDAO.getByAccountId(account.getId()).size());
    }

    @Test
    public void testDeleteInstallment_WithPayments_Success() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now().minusMonths(3), // 3 months ago
                category, true, testLedger);
        installmentController.payInstallment(plan, account); //pay once
        installmentController.payInstallment(plan, account); //pay twice
        //total repayment = 120 + 5% = 126
        //remaining amount = 126 - (10.5 * 5) = 73.5
        //repaid amount = 10.5 *5 =52.5

        boolean deleted = installmentController.deleteInstallment(plan, account);
        assertTrue(deleted);

        assertNull(installmentDAO.getById(plan.getId()));
        assertEquals(0, installmentDAO.getByAccountId(account.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(category.getId()).size());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00)));
    }

    //test pay installment
    @Test
    public void testPayInstallment_Success() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, LocalDate.now(), category, true, testLedger);

        boolean paid = installmentController.payInstallment(plan, account);
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
        assertEquals(2, transactionDAO.getByLedgerId(testLedger.getId()).size());
    }

    @Test
    public void testPayInstallment_FullFlow() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12,
                BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, LocalDate.now(), category, true, testLedger);

        for (int i = 2; i <= 12; i++) {
            boolean paid = installmentController.payInstallment(plan, account);
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
    public void testPayInstallment_AllPeriodsPaid_Failure() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12, BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now().minusMonths(12), // 12 months ago
                category, true, testLedger);
        //all periods already paid
        //total repayment = 120 + 5% = 126
        //remaining amount = 0
        //repaid amount =126

        boolean paid = installmentController.payInstallment(plan, account);
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

    //test edit installment plan
    @Test
    public void testEditInstallment_Success() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12, BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT, LocalDate.now(), category, true, testLedger);
        //remaining amount = 126 -10.5=115.5
        //balance of account =1000-10.5=989.5
        //current debt =20 +115.5=135.5

        boolean edited = installmentController.editInstallment(plan, false, account);
        assertTrue(edited);

        Installment updatedPlan = installmentDAO.getById(plan.getId());
        assertNotNull(updatedPlan);
        assertFalse(updatedPlan.isIncludedInCurrentDebts());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(989.50))); //balance remains the same
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(20.00))); //current debt decreased by 115.5
    }

    @Test
    public void testEditInstallment_Success2() {
        Installment plan = installmentController.createInstallment(account, "Test Installment Plan",
                BigDecimal.valueOf(120.00), 12, BigDecimal.valueOf(5.00), // interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(),
                category, false, testLedger);
        //remaining amount = 126 -10.5=115.5
        //balance of account =1000-10.5=989.5
        //current debt =20

        boolean edited = installmentController.editInstallment(plan, true, account);
        assertTrue(edited);

        Installment updatedPlan = installmentDAO.getById(plan.getId());
        assertNotNull(updatedPlan);
        assertTrue(updatedPlan.isIncludedInCurrentDebts());

        CreditAccount updatedAccount = (CreditAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(989.50))); //balance remains the same
        assertEquals(0, updatedAccount.getCurrentDebt().compareTo(BigDecimal.valueOf(135.50))); //current debt increased by 115.5
    }

    //test getInstallments
    @Test
    public void testGetInstallments() {
        LedgerCategory electronics = testCategories.stream()
                .filter(c -> c.getName().equals("Electronics"))
                .findFirst()
                .orElse(null);
        assertNotNull(electronics);

        //create CreditAccount
        CreditAccount creditAccount = accountController.createCreditAccount("Credit Account", "Credit account notes",
                BigDecimal.valueOf(500.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(2000.00), //credit limit
                BigDecimal.valueOf(150.00), //current debt
                1, 5);
        assertNotNull(creditAccount);

        //create active installment
        Installment installment1 = installmentController.createInstallment(creditAccount, "Installment 1",
                BigDecimal.valueOf(600.00), //total amount
                6,
                BigDecimal.valueOf(2.00),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), electronics, true, testLedger);
        assertNotNull(installment1);
        //create completed installment
        Installment installmentCompleted = installmentController.createInstallment(creditAccount, "Completed Installment",
                BigDecimal.valueOf(300.00), //total amount
                3,
                BigDecimal.valueOf(1.50),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now().minusMonths(4), //4 months ago
                electronics, true, testLedger);
        assertNotNull(installmentCompleted);

        //create second credit account
        CreditAccount creditAccount2 = accountController.createCreditAccount("Credit Account 2", "Another credit account notes",
                BigDecimal.valueOf(400.00), //balance
                true, true, testUser, AccountType.CREDIT_CARD,
                BigDecimal.valueOf(1500.00), //credit limit
                BigDecimal.valueOf(100.00), //current debt
                1, 5);
        assertNotNull(creditAccount2);
        //create active installment for second credit account
        Installment installment2 = installmentController.createInstallment(creditAccount2, "Installment 2",
                BigDecimal.valueOf(300.00), //total amount
                3,
                BigDecimal.valueOf(1.50),  //interest
                Installment.Strategy.EVENLY_SPLIT,
                LocalDate.now(), electronics, true, testLedger);
        assertNotNull(installment2);

        List<Installment> installments = installmentController.getActiveInstallments(creditAccount);
        assertEquals(1, installments.size());
        for( Installment inst : installments) {
            System.out.println("Installment ID: " + inst.getId() +
                    ", Name: " + inst.getName() +
                    ", Total Amount: " + inst.getTotalAmount() +
                    ", Paid Periods: " + inst.getPaidPeriods() +
                    ", Total Periods: " + inst.getTotalPeriods() +
                    ", Category: " +inst.getCategory().getName());
        }
    }
}

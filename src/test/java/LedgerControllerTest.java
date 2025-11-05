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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LedgerControllerTest {
    private Connection connection;
    private UserDAO userDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private TransactionDAO transactionDAO;
    private CategoryDAO categoryDAO;
    private LedgerDAO ledgerDAO;
    private AccountDAO accountDAO;
    private BudgetDAO budgetDAO;
    private InstallmentPlanDAO installmentPlanDAO;

    private UserController userController;
    private LedgerController ledgerController;
    private TransactionController  transactionController;
    private AccountController accountController;
    private BudgetController budgetController;

    private User testUser;
    private Account account1;

    @BeforeEach
    public void setUp() throws SQLException {
        connection=ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        categoryDAO = new CategoryDAO(connection);
        accountDAO = new AccountDAO(connection);
        budgetDAO = new BudgetDAO(connection);
        installmentPlanDAO = new InstallmentPlanDAO(connection);

        userController = new UserController(userDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
        accountController = new AccountController(accountDAO, transactionDAO, installmentPlanDAO);
        budgetController = new BudgetController(budgetDAO);

        userController.register("testuser", "password123"); // create test user and insert into db
        testUser=userController.login("testuser", "password123"); // login to set current user

        account1=accountController.createBasicAccount("Test Account 1",
                BigDecimal.valueOf(1000.00),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                null,
                true,
                true);
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

    @Test
    public void testCreateLedger() throws SQLException {
        Ledger ledger = ledgerController.createLedger("Test Ledger", testUser);
        assertNotNull(ledger);

        assertNotNull(ledgerDAO.getById(ledger.getId()));
        assertEquals(1, testUser.getLedgers().size());
        assertEquals(2, ledger.getBudgets().size()); // assuming 2 is the expected number of budgets created
        assertEquals(17, ledger.getCategories().size()); //assuming 17 is the expected number of categories created


        for (LedgerCategory category : ledger.getCategories().stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .collect(Collectors.toList())) {
            assertEquals(2, category.getBudgets().size()); //each expense category should have 2 budgets
            Budget monthlyBudget = category.getBudgets().stream()
                    .filter(b -> b.getPeriod() == Budget.Period.MONTHLY)
                    .findFirst()
                    .orElse(null);
            Budget yearlyBudget = category.getBudgets().stream()
                    .filter(b -> b.getPeriod() == Budget.Period.YEARLY)
                    .findFirst()
                    .orElse(null);
            assertNotNull(monthlyBudget);
            assertNotNull(yearlyBudget);
            assertNotNull(budgetDAO.getById(monthlyBudget.getId()));
            assertNotNull(budgetDAO.getById(yearlyBudget.getId()));
        }



        //print details
        List<LedgerCategory> expense= ledger.getCategories().stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .filter(cat -> cat.getParent() == null)
                .collect(Collectors.toList());
        List<LedgerCategory> income= ledger.getCategories().stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .filter(cat -> cat.getParent() == null)
                .collect(Collectors.toList());
        for(LedgerCategory cat : expense){
            System.out.println("Expense Category: " + cat.getName());
            assertEquals(2, cat.getBudgets().size());
            for(LedgerCategory sub: cat.getChildren()){
                System.out.println("  Expense Subcategory: " + sub.getName());
                assertEquals(2, sub.getBudgets().size());
            }
        }

        for(LedgerCategory cat : income){
            System.out.println("Income Category: " + cat.getName());
            for(LedgerCategory sub: cat.getChildren()){
                System.out.println("  Income Subcategory: " + sub.getName());
            }
        }


    }

    @Test
    public void testCreateLedger_DuplicateName() {
        Ledger ledger = ledgerController.createLedger("Duplicate Ledger", testUser);
        assertNotNull(ledger);

        assertNull(ledgerController.createLedger("Duplicate Ledger", testUser));
    }

    @Test
    public void testDeleteLedger_NullTransaction() throws SQLException {
        Ledger ledger = ledgerController.createLedger("Ledger To Delete", testUser);
        assertNotNull(ledger);

        boolean deleted = ledgerController.deleteLedger(ledger);
        assertTrue(deleted);

        Ledger deletedLedger = ledgerDAO.getById(ledger.getId());
        assertNull(deletedLedger);
        assertEquals(0, ledgerCategoryDAO.countCategoryInDatabase());
    }

    @Test
    public void testDeleteLedger_WithTransactions() throws SQLException {
        Ledger ledger = ledgerController.createLedger("Ledger With Transactions", testUser);
        assertNotNull(ledger);

        LedgerCategory food = ledger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        LedgerCategory salary = ledger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        //create transactions
        Transaction tx1=transactionController.createExpense(ledger, account1, food, null, LocalDate.now(), BigDecimal.valueOf(10.00));
        Transaction tx2=transactionController.createIncome(ledger, account1, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        //delete ledger
        boolean deleted = ledgerController.deleteLedger(ledger);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));
    }

    @Test
    public void testDeleteLedger_WithBudgets() throws SQLException {
        Ledger ledger = ledgerController.createLedger("Ledger With Budgets", testUser);
        assertNotNull(ledger);

        LedgerCategory food = ledger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);


        //delete ledger
        boolean deleted = ledgerController.deleteLedger(ledger);
        assertTrue(deleted);
        assertEquals(0, ledgerCategoryDAO.countCategoryInDatabase());
    }

    @Test
    public void testCopyLedger() throws SQLException {
        Ledger originalLedger = ledgerController.createLedger("Original Ledger", testUser);
        assertNotNull(originalLedger);

        Ledger copiedLedger = ledgerController.copyLedger(originalLedger);
        assertNotNull(copiedLedger);
        assertEquals("Original Ledger Copy", copiedLedger.getName());
        assertEquals(2, testUser.getLedgers().size());
        assertEquals(originalLedger.getCategories().size(), copiedLedger.getCategories().size());

        for (LedgerCategory originalCategory : originalLedger.getCategories()) {
            LedgerCategory copiedCategory = copiedLedger.getCategories().stream()
                    .filter(cat -> cat.getName().equals(originalCategory.getName()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(copiedCategory);
            assertEquals(originalCategory.getType(), copiedCategory.getType());
            assertEquals(originalCategory.getChildren().size(), copiedCategory.getChildren().size());
        }
    }

    @Test
    public void testRenameLedger_Success() throws SQLException {
        Ledger ledger = ledgerController.createLedger("Ledger To Rename", testUser);
        assertNotNull(ledger);

        boolean renamed = ledgerController.renameLedger(ledger, "Renamed Ledger");
        assertTrue(renamed);
        assertEquals("Renamed Ledger",ledger.getName());
    }
}

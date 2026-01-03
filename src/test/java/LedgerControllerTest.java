import com.ledger.business.AccountController;
import com.ledger.business.LedgerController;
import com.ledger.business.TransactionController;
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

public class LedgerControllerTest {
    private Connection connection;

    private LedgerCategoryDAO ledgerCategoryDAO;
    private TransactionDAO transactionDAO;
    private LedgerDAO ledgerDAO;
    private BudgetDAO budgetDAO;
    private AccountDAO accountDAO;

    private LedgerController ledgerController;
    private TransactionController  transactionController;

    private User testUser;
    private Account account;
    private LedgerCategory food;
    private LedgerCategory salary;
    private List<LedgerCategory> testCategories;
    private Ledger testLedger;

    @BeforeEach
    public void setUp() {
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        accountDAO = new AccountDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        budgetDAO = new BudgetDAO(connection);


        UserController userController = new UserController(userDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        AccountController accountController = new AccountController(accountDAO, transactionDAO);

        userController.register("test user", "password123"); // create test user and insert into db
        testUser = userController.login("test user", "password123"); // login to set current user

        account = accountController.createAccount("Test Account", BigDecimal.valueOf(1000.00), true,
                true);
        testLedger = ledgerController.createLedger("Test Ledger");

        testCategories = ledgerCategoryDAO.getTreeByLedger(ledgerDAO.getById(testLedger.getId()));
        food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
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
    public void testCreateLedger() {
        Ledger ledger = ledgerController.createLedger("Test Ledger 1"); //create another ledger
        assertNotNull(ledger);
        assertNotNull(ledgerDAO.getById(ledger.getId()));
        assertEquals(2, ledgerDAO.getLedgersByUserId(testUser.getId()).size());
        assertNotNull(budgetDAO.getBudgetByLedger(ledger, Period.MONTHLY)); //ledger-level budgets
        assertNotNull(budgetDAO.getBudgetByLedger(ledger, Period.YEARLY));
        assertEquals(17, ledgerCategoryDAO.getTreeByLedger(ledger).size()); //default categories
        List<LedgerCategory> categories= ledgerCategoryDAO.getTreeByLedger(ledger);
        List<LedgerCategory> expenseCategories= categories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .toList();
        List<LedgerCategory> incomeCategories= categories.stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(3, incomeCategories.size());
        assertEquals(14, expenseCategories.size());
        for(LedgerCategory cat : expenseCategories){ //all expense categories should have budgets
            assertNotNull(budgetDAO.getBudgetByCategory(cat, Period.MONTHLY));
            assertNotNull(budgetDAO.getBudgetByCategory(cat, Period.YEARLY));
        }
        for(LedgerCategory cat : incomeCategories){ //income categories should not have budgets
            assertNull(budgetDAO.getBudgetByCategory(cat, Period.MONTHLY));
            assertNull(budgetDAO.getBudgetByCategory(cat, Period.YEARLY));
        }
    }

    @Test
    public void testCreateLedger_Failure() {
        assertNotNull(ledgerController.createLedger("Duplicate Ledger")); //first creation should succeed
        assertNull(ledgerController.createLedger("Duplicate Ledger")); //duplicate name should fail
        assertNull(ledgerController.createLedger("")); //empty name should fail
        assertNull(ledgerController.createLedger(null)); //null name should fail
    }

    @Test
    public void testDeleteLedger() {
        //create transactions
        Transaction tx1=transactionController.createExpense(testLedger, account, food, null, LocalDate.now(), BigDecimal.valueOf(10.00));
        Transaction tx2=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));
        Transaction tx3=transactionController.createTransfer(testLedger, account, null, null, LocalDate.now(), BigDecimal.valueOf(100.00));

        //delete ledger
        assertTrue(ledgerController.deleteLedger(testLedger));
        assertNull(ledgerDAO.getById(testLedger.getId()));
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));
        assertNull(transactionDAO.getById(tx3.getId()));
        assertEquals(0, ledgerCategoryDAO.getTreeByLedger(testLedger).size()); //all categories should be deleted
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size()); //transactions should be deleted

        //verify balance of account
        Account updatedAccount = accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //delete budgets
        assertNull(budgetDAO.getBudgetByLedger(testLedger, Period.MONTHLY));
        assertNull(budgetDAO.getBudgetByLedger(testLedger, Period.YEARLY));
        for(LedgerCategory category : testCategories) {
            assertNull(ledgerCategoryDAO.getById(category.getId()));
            assertNull(budgetDAO.getBudgetByCategory(category, Period.MONTHLY));
            assertNull(budgetDAO.getBudgetByCategory(category, Period.YEARLY));
        }
    }

    @Test
    public void testRenameLedger_Success() {
        assertTrue(ledgerController.renameLedger(testLedger, "Renamed Ledger"));
        Ledger updatedLedger = ledgerDAO.getById(testLedger.getId());
        assertEquals("Renamed Ledger", updatedLedger.getName());
    }

    @Test
    public void testRenameLedger_Failure() {
        ledgerController.createLedger("Ledger One"); //create another ledger
        assertFalse(ledgerController.renameLedger(testLedger, "Ledger One")); //duplicate name
        assertFalse(ledgerController.renameLedger(testLedger, "")); //empty name
        assertFalse(ledgerController.renameLedger(testLedger, null)); //null name
        assertFalse(ledgerController.renameLedger(null, "New Name"));
    }

    //test getLedger
    @Test
    public void testGetLedgersByUser() {
        ledgerController.createLedger("Second Ledger"); //create second ledger
        Ledger deletedLedger = ledgerController.createLedger("Deleted Ledger"); //create third ledger to be deleted
        assertEquals(3, ledgerController.getLedgersByUser(testUser).size()); //get ledgers
        assertTrue(ledgerController.deleteLedger(deletedLedger)); //delete ledger
        assertEquals(2, ledgerController.getLedgersByUser(testUser).size()); //get ledgers again
    }
}

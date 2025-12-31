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
        Ledger ledger = ledgerController.createLedger("Test Ledger");
        assertNotNull(ledger);

        assertNotNull(ledgerDAO.getById(ledger.getId()));
        assertEquals(1, ledgerDAO.getLedgersByUserId(testUser.getId()).size());
        assertNotNull( budgetDAO.getBudgetByLedger(ledger, Period.MONTHLY));
        assertNotNull(budgetDAO.getBudgetByLedger(ledger, Period.YEARLY));
        assertEquals(17, ledgerCategoryDAO.getTreeByLedger(ledger).size());
        List<LedgerCategory> categories= ledgerCategoryDAO.getTreeByLedger(ledger);

        //print details
        List<LedgerCategory> expense= categories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .filter(cat -> cat.getParent() == null)
                .toList();
        List<LedgerCategory> income= categories.stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .filter(cat -> cat.getParent() == null)
                .toList();

        System.out.println("Expense Categories:");
        for(LedgerCategory cat : expense){
            System.out.println(" Expense Category: " + cat.getName());
            Budget monthlyBudget = budgetDAO.getBudgetByCategory(cat, Period.MONTHLY);
            Budget yearlyBudget = budgetDAO.getBudgetByCategory(cat, Period.YEARLY);
            assertNotNull(monthlyBudget);
            assertNotNull(yearlyBudget);
            System.out.println(" Monthly Budget: " + monthlyBudget.getAmount());
            System.out.println(" Yearly Budget: " + yearlyBudget.getAmount());

            for(LedgerCategory sub: categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("  Expense Subcategory: " + sub.getName());
                Budget subMonthlyBudget = budgetDAO.getBudgetByCategory(sub, Period.MONTHLY);
                Budget subYearlyBudget = budgetDAO.getBudgetByCategory(sub, Period.YEARLY);
                assertNotNull(subMonthlyBudget);
                assertNotNull(subYearlyBudget);
                System.out.println("  Monthly Budget: " + subMonthlyBudget.getAmount());
                System.out.println("  Yearly Budget: " + subYearlyBudget.getAmount());
            }
        }

        System.out.println("Income Categories:");
        for(LedgerCategory cat : income){
            System.out.println(" Income Category: " + cat.getName());
            Budget monthlyBudget = budgetDAO.getBudgetByCategory(cat, Period.MONTHLY);
            Budget yearlyBudget = budgetDAO.getBudgetByCategory(cat, Period.YEARLY);
            assertNull(monthlyBudget);
            assertNull(yearlyBudget);
            for(LedgerCategory sub: categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("   Income Subcategory: " + sub.getName());
                Budget subMonthlyBudget = budgetDAO.getBudgetByCategory(sub, Period.MONTHLY);
                Budget subYearlyBudget = budgetDAO.getBudgetByCategory(sub, Period.YEARLY);
                assertNull(subMonthlyBudget);
                assertNull(subYearlyBudget);
            }
        }
    }

    @Test
    public void testCreateLedger_Failure() {
        Ledger ledger = ledgerController.createLedger("Duplicate Ledger");
        assertNotNull(ledger);
        assertNull(ledgerController.createLedger("Duplicate Ledger"));
    }

    @Test
    public void testDeleteLedger() {
        Ledger ledger = ledgerController.createLedger("Ledger With Transactions");
        assertNotNull(ledger);

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedger(ledger);
        LedgerCategory food = categories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        LedgerCategory salary = categories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        //create transactions
        Transaction tx1=transactionController.createExpense(ledger, account, food, null, LocalDate.now(), BigDecimal.valueOf(10.00));
        Transaction tx2=transactionController.createIncome(ledger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        //delete ledger
        boolean deleted = ledgerController.deleteLedger(ledger);
        assertTrue(deleted);
        assertNull(ledgerDAO.getById(ledger.getId()));
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));
        assertEquals(0, transactionDAO.getByCategoryId(food.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, ledgerCategoryDAO.getTreeByLedger(ledger).size()); //all categories should be deleted
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size()); //transactions should be deleted

        //verify balance of account
        Account updatedAccount = accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //delete budgets
        assertNull(budgetDAO.getBudgetByLedger(ledger, Period.MONTHLY));
        assertNull(budgetDAO.getBudgetByLedger(ledger, Period.YEARLY));
        for(LedgerCategory category : categories){
            assertNull(ledgerCategoryDAO.getById(category.getId()));
            assertNull(budgetDAO.getBudgetByCategory(category, Period.MONTHLY));
            assertNull(budgetDAO.getBudgetByCategory(category, Period.YEARLY));
        }
    }

    @Test
    public void testRenameLedger_Success() {
        Ledger ledger = ledgerController.createLedger("Ledger To Rename");
        assertNotNull(ledger);

        boolean renamed = ledgerController.renameLedger(ledger, "Renamed Ledger");
        assertTrue(renamed);
        Ledger updatedLedger = ledgerDAO.getById(ledger.getId());
        assertEquals("Renamed Ledger", updatedLedger.getName());
    }

    @Test
    public void testRenameLedger_Failure() {
        Ledger ledger1 = ledgerController.createLedger("Ledger One");
        assertNotNull(ledger1);
        Ledger ledger2 = ledgerController.createLedger("Ledger Two");
        assertNotNull(ledger2);
        assertFalse(ledgerController.renameLedger(ledger2, "Ledger One"));
        assertFalse(ledgerController.renameLedger(ledger2, "")); //empty name
        assertFalse(ledgerController.renameLedger(ledger2, null)); //null name
        assertFalse(ledgerController.renameLedger(null, "New Name"));
    }

    //test getLedger
    @Test
    public void testGetLedgersByUser() {
        //create ledger
        Ledger secondLedger = ledgerController.createLedger("Second Ledger");
        assertNotNull(secondLedger);

        //create ledger and delete it
        Ledger deletedLedger = ledgerController.createLedger("Deleted Ledger");
        assertNotNull(deletedLedger);
        assertEquals(2, ledgerController.getLedgersByUser(testUser).size());
        assertTrue(ledgerController.deleteLedger(deletedLedger)); //delete ledger
        assertEquals(1, ledgerController.getLedgersByUser(testUser).size());
    }
}

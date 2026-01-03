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
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class LedgerCategoryControllerTest {
    private Connection connection;

    private Ledger testLedger;
    private Account account;
    private LedgerCategory food;
    private LedgerCategory lunch;
    private LedgerCategory salary;
    private LedgerCategory shopping;
    private LedgerCategory bonus;
    private LedgerCategoryController ledgerCategoryController;
    private TransactionController transactionController;

    private LedgerCategoryDAO ledgerCategoryDAO;
    private BudgetDAO budgetDAO;
    private TransactionDAO transactionDAO;
    private AccountDAO accountDAO;

    @BeforeEach
    public void setUp() {
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        accountDAO = new AccountDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        ledgerCategoryController = new LedgerCategoryController(ledgerCategoryDAO, transactionDAO, budgetDAO, accountDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        AccountController accountController= new AccountController(accountDAO, transactionDAO);

        userController.register("test user", "password123");
        userController.login("test user", "password123");

        testLedger=ledgerController.createLedger("Test Ledger");

        List<LedgerCategory> testCategories=ledgerCategoryDAO.getTreeByLedger(testLedger);
        food=testCategories.stream()
                .filter(cat->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        lunch=testCategories.stream()
                .filter(cat->cat.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        salary=testCategories.stream()
                .filter(cat->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        shopping=testCategories.stream()
                .filter(cat->cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        bonus=testCategories.stream()
                .filter(cat->cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);

        account =accountController.createAccount("Test Account", BigDecimal.valueOf(1000.00), true,
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
    public void testCreate_Failure() {
        assertNull(ledgerCategoryController.createCategory(null, testLedger, CategoryType.EXPENSE)); //name is null
        assertNull(ledgerCategoryController.createCategory("", testLedger, CategoryType.EXPENSE)); //name is empty
        assertNull(ledgerCategoryController.createCategory("Food", null, CategoryType.INCOME)); //ledger is null
        assertNull(ledgerCategoryController.createCategory("Salary", testLedger, null)); //type is null
        assertNull(ledgerCategoryController.createCategory("Food", testLedger, CategoryType.EXPENSE)); //duplicate name
        assertNull(ledgerCategoryController.createSubCategory("Snack", lunch)); //create subcategory of a subcategory
        assertNull(ledgerCategoryController.createSubCategory("Lunch", food)); //duplicate subcategory
    }

    //create category of first-level
    @Test
    public void testCreateCategory_Success() {
        LedgerCategory category=ledgerCategoryController.createCategory("Test", testLedger, CategoryType.EXPENSE); //create category
        assertNotNull(ledgerCategoryDAO.getById(category.getId()));
        assertNotNull(budgetDAO.getBudgetByCategory(category, Period.MONTHLY));
        assertNotNull(budgetDAO.getBudgetByCategory(category, Period.YEARLY));
        assertEquals(18, ledgerCategoryDAO.getTreeByLedger(testLedger).size()); //exists in DB

        LedgerCategory subCategory=ledgerCategoryController.createSubCategory("Test subcategory", food); //create sub-category
        assertNotNull(ledgerCategoryDAO.getById(subCategory.getId())); //exists in DB
        assertEquals(food.getId(), subCategory.getParent().getId());
        assertNotNull(budgetDAO.getBudgetByCategory(subCategory, Period.MONTHLY));
        assertNotNull(budgetDAO.getBudgetByCategory(subCategory, Period.YEARLY));
        assertEquals(19, ledgerCategoryDAO.getTreeByLedger(testLedger).size()); //exists in DB

        List<LedgerCategory> categories=ledgerCategoryDAO.getCategoriesByParentId(food.getId(), testLedger);
        assertEquals(4, categories.size()); //exists in DB under parent
        assertTrue(categories.stream().anyMatch(cat->cat.getId() == subCategory.getId()));
    }

    //delete sub-category
    @Test
    public void testDeleteSubcategory() {
        Expense tx1=transactionController.createExpense(testLedger, account, lunch, null, LocalDate.now(), BigDecimal.valueOf(20.00));
        Expense tx2=transactionController.createExpense(testLedger, account, lunch, null, LocalDate.now(), BigDecimal.valueOf(30.00));

        assertTrue(ledgerCategoryController.deleteCategory(lunch)); //delete sub-category
        assertNull(ledgerCategoryDAO.getById(lunch.getId())); //subcategory deleted from DB
        assertNull(transactionDAO.getById(tx1.getId())); //transaction deleted from DB
        assertNull(transactionDAO.getById(tx2.getId())); //transaction deleted from DB
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
        assertNull(budgetDAO.getBudgetByCategory(lunch, Period.MONTHLY)); //budgets deleted
        assertNull(budgetDAO.getBudgetByCategory(lunch, Period.YEARLY));
        assertEquals(16, ledgerCategoryDAO.getTreeByLedger(testLedger).size()); //one less category in DB
        assertFalse(ledgerCategoryDAO.getTreeByLedger(testLedger).stream().anyMatch(cat->cat.getId() == lunch.getId())); //not under parent anymore

        //verify balance of account
        Account updatedAccount=accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //delete category with transactions
    @Test
    public void testDeleteCategory() {
        Income tx1=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));
        Income tx2=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(500.00));

        assertTrue(ledgerCategoryController.deleteCategory(salary)); //delete category
        assertNull(ledgerCategoryDAO.getById(salary.getId())); //category deleted from DB
        assertNull(transactionDAO.getById(tx1.getId())); //transaction deleted from DB
        assertNull(transactionDAO.getById(tx2.getId())); //transaction deleted from DB
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
        assertNull(budgetDAO.getBudgetByCategory(salary, Period.MONTHLY));
        assertNull(budgetDAO.getBudgetByCategory(salary, Period.YEARLY));
        assertEquals(16, ledgerCategoryDAO.getTreeByLedger(testLedger).size()); //one less category in DB
        assertFalse(ledgerCategoryDAO.getTreeByLedger(testLedger).stream().anyMatch(cat->cat.getId() == salary.getId()));

        //verify balance of account
        Account updatedAccount=accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //delete category with sub-categories
    @Test
    public void testDeleteCategory_Failure() {
        assertFalse(ledgerCategoryController.deleteCategory(food)); //should fail because it has sub-categories
        assertNotNull(ledgerCategoryDAO.getById(food.getId())); //category should still exist in DB
        assertFalse(ledgerCategoryController.deleteCategory(null)); //null category
    }

    //rename category successfully
    @Test
    public void testRenameCategory_Success() {
        assertTrue( ledgerCategoryController.rename(food, "Groceries"));
        LedgerCategory updatedCategory=ledgerCategoryDAO.getById(food.getId()); //get updated from DB
        assertEquals("Groceries", updatedCategory.getName());
    }

    @Test
    public void testRenameCategory_Failure() {
        assertFalse(ledgerCategoryController.rename(null, "New Category Name"));
        assertFalse(ledgerCategoryController.rename(food, null));
        assertFalse(ledgerCategoryController.rename(food, ""));
        assertFalse(ledgerCategoryController.rename(food, "Salary"));
    }

    //promote sub-category to top-level
    @Test
    public void testPromote_Success() {
        assertNotNull(lunch.getParent());
        assertEquals(food.getId(), lunch.getParent().getId());
        //promote lunch
        assertTrue(ledgerCategoryController.promoteSubCategory(lunch));

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedger(testLedger); //get updated list from DB
        assertEquals(17, categories.size()); //still 17 categories
        LedgerCategory promotedCategory=categories.stream() // find lunch
                .filter(cat->cat.getId() == lunch.getId())
                .findFirst()
                .orElse(null);
        assertNotNull(promotedCategory);
        assertNull(promotedCategory.getParent()); //no parent in DB

        List<LedgerCategory> topLevelCategories=categories.stream()
                .filter(cat->cat.getParent() == null)
                .toList();
        assertTrue(topLevelCategories.stream().anyMatch(cat->cat.getId() == lunch.getId())); //exists as top-level category in DB
        assertEquals(10, topLevelCategories.stream().filter(cat->cat.getType() == CategoryType.EXPENSE).toList().size()); //one more top-level expense category
    }

    @Test
    public void testPromote_Failure() {
        assertFalse(ledgerCategoryController.promoteSubCategory(food)); //not a sub-category
        assertFalse(ledgerCategoryController.promoteSubCategory(null)); //null category
    }

    @Test
    public void testDemote_Success() {
        assertNull(salary.getParent()); //salary is top-level category
        assertNull(bonus.getParent()); //bonus is top-level category
        assertTrue(ledgerCategoryController.demoteCategory(bonus, salary)); //demote bonus under salary

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedger(testLedger); //get updated list from DB
        assertEquals(17, categories.size()); //still 17 categories
        LedgerCategory demotedCategory=categories.stream() // find bonus
                .filter(cat->cat.getId() == bonus.getId())
                .findFirst()
                .orElse(null);
        assertNotNull(demotedCategory);
        assertEquals(salary.getId(), demotedCategory.getParent().getId()); //parent set in DB

        List<LedgerCategory> rootCategories=categories.stream()
                .filter(cat->cat.getParent() == null)
                .toList();
        List<LedgerCategory> incomeCategories=rootCategories.stream()
                .filter(cat->cat.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(2, incomeCategories.size());
    }

    @Test
    public void testDemote_Failure() {
        assertFalse(ledgerCategoryController.demoteCategory(food, salary)); //different types
        assertFalse(ledgerCategoryController.demoteCategory(salary, null)); //parent is null
        assertFalse(ledgerCategoryController.demoteCategory(null, salary)); //category is null
        assertFalse(ledgerCategoryController.demoteCategory(salary, salary)); //same category

        assertFalse(ledgerCategoryController.demoteCategory(shopping, lunch)); //parent is not top-level
        assertFalse(ledgerCategoryController.demoteCategory(lunch, shopping)); //category is not top-level
        assertFalse(ledgerCategoryController.demoteCategory(food, shopping)); //category has children
    }

    @Test
    public void testChangeParent_Success() {
        assertEquals(food.getId(), lunch.getParent().getId()); //initial parent is food
        assertTrue(ledgerCategoryController.changeParent(lunch, shopping)); //change parent to shopping

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedger(testLedger); //get updated list from DB
        LedgerCategory updatedCategory = categories.stream() // find lunch
                .filter(cat -> cat.getId() == lunch.getId())
                .findFirst()
                .orElse(null);
        assertNotNull(updatedCategory);
        assertEquals(shopping.getId(), updatedCategory.getParent().getId()); //parent set in DB
        assertEquals(1, ledgerCategoryDAO.getCategoriesByParentId(shopping.getId(), testLedger).size()); //lunch added to shopping's children
        assertEquals(2, ledgerCategoryDAO.getCategoriesByParentId(food.getId(), testLedger).size()); //lunch removed from food's children
    }

    //category and new parent are the same
    @Test
    public void testChangeParent_Failure() {
        assertFalse(ledgerCategoryController.changeParent(lunch, lunch)); //same category
        assertFalse(ledgerCategoryController.changeParent(lunch, salary)); //different types
        assertFalse(ledgerCategoryController.changeParent(lunch, lunch)); //new parent is not top-level
        assertFalse(ledgerCategoryController.changeParent(lunch, null)); //new parent is null
        assertFalse(ledgerCategoryController.changeParent(null, lunch)); //category is null
        assertFalse(ledgerCategoryController.changeParent(food, lunch)); //category is not sub-category
    }

    //test LedgerCategory tree structure
    @Test
    public void testCategoryTreeStructure() {
        List<LedgerCategory> categories = ledgerCategoryController.getCategoryTreeByLedger(testLedger);
        assertEquals(17, categories.size());

        List<LedgerCategory> rootCategories = categories.stream()
                .filter(c -> c.getParent() == null)
                .toList();
        assertEquals(12, rootCategories.size());

        List<LedgerCategory> incomeRootCategories = rootCategories.stream()
                .filter(c -> c.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(3, incomeRootCategories.size());

        List<LedgerCategory> expenseRootCategories = rootCategories.stream()
                .filter(c -> c.getType() == CategoryType.EXPENSE)
                .toList();
        assertEquals(9, expenseRootCategories.size());
    }
}

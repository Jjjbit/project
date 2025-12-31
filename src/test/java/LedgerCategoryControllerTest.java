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
    private List<LedgerCategory> testCategories;

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

        testCategories=ledgerCategoryDAO.getTreeByLedger(testLedger);

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
        assertNull(ledgerCategoryController.createCategory(null, testLedger, CategoryType.EXPENSE));
        assertNull(ledgerCategoryController.createCategory("", testLedger, CategoryType.EXPENSE));
        assertNull(ledgerCategoryController.createCategory("Food", null, CategoryType.EXPENSE));
        assertNull(ledgerCategoryController.createCategory("Salary", testLedger, null));
        //duplicate name
        assertNull(ledgerCategoryController.createCategory("Food", testLedger, CategoryType.EXPENSE));
        LedgerCategory breakfast=testCategories.stream()
                .filter(cat->cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        assertNull(ledgerCategoryController.createSubCategory("Snack", breakfast));
    }

    //create category of first-level
    @Test
    public void testCreateCategory_Success() {
        LedgerCategory category=ledgerCategoryController.createCategory("Test", testLedger, CategoryType.EXPENSE);
        assertNotNull(category);
        assertNotNull(ledgerCategoryDAO.getById(category.getId()));

        Budget monthlyBudget=budgetDAO.getBudgetByCategory(category, Period.MONTHLY);
        Budget yearlyBudget=budgetDAO.getBudgetByCategory(category, Period.YEARLY);
        assertNotNull(monthlyBudget);
        assertNotNull(yearlyBudget);
    }

    //create sub-category
    @Test
    public void testCreateSubCategory_Success() {
        LedgerCategory food=testCategories.stream()
                .filter(cat->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        LedgerCategory subCategory=ledgerCategoryController.createSubCategory("Test", food);
        assertNotNull(subCategory); //created successfully
        assertNotNull(ledgerCategoryDAO.getById(subCategory.getId())); //exists in DB

        assertEquals(food.getId(), subCategory.getParent().getId());

        Budget monthlyBudget=budgetDAO.getBudgetByCategory(subCategory, Period.MONTHLY);
        Budget yearlyBudget=budgetDAO.getBudgetByCategory(subCategory, Period.YEARLY);
        assertNotNull(monthlyBudget);
        assertNotNull(yearlyBudget);

        List<LedgerCategory> categories=ledgerCategoryDAO.getCategoriesByParentId(food.getId(), testLedger);
        assertEquals(4, categories.size()); //exists in DB under parent
        assertTrue(categories.stream().anyMatch(cat->cat.getId() == subCategory.getId()));
    }

    //delete sub-category
    @Test
    public void testDeleteSubcategory() {
        LedgerCategory breakfast=testCategories.stream()
                .filter(cat->cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);

        Expense tx1=transactionController.createExpense(testLedger, account, breakfast, null, LocalDate.now(), BigDecimal.valueOf(20.00));
        Expense tx2=transactionController.createExpense(testLedger, account, breakfast, null, LocalDate.now(), BigDecimal.valueOf(30.00));

        assertTrue(ledgerCategoryController.deleteCategory(breakfast));
        assertNull(ledgerCategoryDAO.getById(breakfast.getId())); //subcategory deleted from DB
        assertNull(transactionDAO.getById(tx1.getId())); //transaction deleted from DB
        assertNull(transactionDAO.getById(tx2.getId())); //transaction deleted from DB
        assertEquals(0, transactionDAO.getByCategoryId(breakfast.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
        assertNull(budgetDAO.getBudgetByCategory(breakfast, Period.MONTHLY));
        assertNull(budgetDAO.getBudgetByCategory(breakfast, Period.YEARLY));

        //verify balance of account
        Account updatedAccount=accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    //delete category with transactions
    @Test
    public void testDeleteCategory() {
        LedgerCategory salary=testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Income tx1=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));
        Income tx2=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(500.00));

        assertTrue( ledgerCategoryController.deleteCategory(salary));
        assertNull(ledgerCategoryDAO.getById(salary.getId())); //category deleted from DB
        assertNull(transactionDAO.getById(tx1.getId())); //transaction deleted from DB
        assertNull(transactionDAO.getById(tx2.getId())); //transaction deleted from DB
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());
        assertNull(budgetDAO.getBudgetByCategory(salary, Period.MONTHLY));
        assertNull(budgetDAO.getBudgetByCategory(salary, Period.YEARLY));

        //verify balance of account
        Account updatedAccount=accountDAO.getAccountById(account.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedger(testLedger);
        assertEquals(16, categories.size());

        List<LedgerCategory> parents = categories.stream()
                .filter(cat->cat.getParent() == null)
                .toList();

        List<LedgerCategory> incomeCategories=parents.stream()
                .filter(cat->cat.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(2, incomeCategories.size());

        List<LedgerCategory> expenseCategories=parents.stream()
                .filter(cat->cat.getType() == CategoryType.EXPENSE)
                .toList();
        assertEquals(9, expenseCategories.size());

        System.out.println("Income Categories:");
        for(LedgerCategory cat : incomeCategories){
            System.out.println(" Category Name: "+cat.getName());
            for(LedgerCategory child : categories.stream()
                    .filter(c->c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("   Child Name: "+child.getName());
            }
        }

        System.out.println("Expense Categories:");
        for(LedgerCategory cat : expenseCategories){
            System.out.println(" Category Name: "+cat.getName());
            for(LedgerCategory child : categories.stream()
                    .filter(c->c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("   Child Name: "+child.getName());
            }
        }
    }

    //delete category with transactions, migrate to another category
//    @Test
//    public void testDeleteLedgerCategory_KeepTransactions() {
//        LedgerCategory salary=testCategories.stream()
//                .filter(cat -> cat.getName().equals("Salary"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(salary);
//        LedgerCategory bonus=testCategories.stream()
//                .filter(cat -> cat.getName().equals("Bonus"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(bonus);
//
//        Transaction tx1=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));
//
//        boolean result = ledgerCategoryController.deleteCategory(salary, false, bonus);
//        assertTrue(result);
//        assertNull(ledgerCategoryDAO.getById(salary.getId())); //salary deleted from DB
//        assertNotNull(transactionDAO.getById(tx1.getId())); //transaction should exist in DB
//        assertEquals(1, transactionDAO.getByCategoryId(bonus.getId()).size()); //transaction migrated to bonus
//        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
//        assertEquals(tx1.getId(), transactionDAO.getByCategoryId(bonus.getId()).getFirst().getId());
//        assertEquals(tx1.getId(), transactionDAO.getByLedgerId(testLedger.getId()).getFirst().getId());
//
//        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
//        assertEquals(16, categories.size()); //one category less in DB
//
//        List<LedgerCategory> parents=categories.stream()
//                .filter(cat->cat.getParent() == null)
//                .toList();
//
//        List<LedgerCategory> incomeCategories=parents.stream()
//                .filter(cat->cat.getType() == CategoryType.INCOME)
//                .toList();
//        assertEquals(2, incomeCategories.size());
//        List<LedgerCategory> expenseCategories=parents.stream()
//                .filter(cat->cat.getType() == CategoryType.EXPENSE)
//                .toList();
//        assertEquals(9, expenseCategories.size());
//
//        System.out.println("Income Categories:");
//        for(LedgerCategory cat : incomeCategories){
//            System.out.println(" Category Name: "+cat.getName());
//            for(LedgerCategory child : categories.stream()
//                    .filter(c->c.getParent() != null && c.getParent().getId() == cat.getId())
//                    .toList()){
//                System.out.println("   Child Name: "+child.getName());
//            }
//        }
//
//        System.out.println("Expense Categories:");
//        for(LedgerCategory cat : expenseCategories){
//            System.out.println(" Category Name: "+cat.getName());
//            for(LedgerCategory child : categories.stream()
//                    .filter(c->c.getParent() != null && c.getParent().getId() == cat.getId())
//                    .toList()){
//                System.out.println("   Child Name: "+child.getName());
//            }
//        }
//    }

    //delete category with sub-categories
    @Test
    public void testDeleteCategory_Failure() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);

        assertFalse(ledgerCategoryController.deleteCategory(food)); //should fail because it has sub-categories
        assertNotNull(ledgerCategoryDAO.getById(food.getId())); //category should still exist in DB
        assertFalse(ledgerCategoryController.deleteCategory(null)); //null category
    }

    //rename category successfully
    @Test
    public void testRenameLedgerCategory_Success() {
        LedgerCategory foodCategory = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(foodCategory);

        boolean result = ledgerCategoryController.rename(foodCategory, "Groceries");
        assertTrue(result);
        assertEquals("Groceries", foodCategory.getName());

        LedgerCategory updatedCategory=ledgerCategoryDAO.getById(foodCategory.getId());
        assertEquals("Groceries", updatedCategory.getName());
    }

    @Test
    public void testRenameLedgerCategory_Failure() {
        assertFalse(ledgerCategoryController.rename(null, "New Category Name"));
        assertFalse(ledgerCategoryController.rename(testCategories.getFirst(), null));
        assertFalse(ledgerCategoryController.rename(testCategories.getFirst(), ""));
        assertFalse(ledgerCategoryController.rename(testCategories.getFirst(), "Salary"));
    }

    //promote sub-category to top-level
    @Test
    public void testPromoteSubCategory_Success() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        LedgerCategory food=testCategories.stream()
                .filter((cat)->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        assertNotNull(breakfast.getParent());
        assertEquals(food.getId(), breakfast.getParent().getId());

        boolean result = ledgerCategoryController.promoteSubCategory(breakfast);
        assertTrue(result);
        assertNull(breakfast.getParent());

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedger(testLedger);
        LedgerCategory promotedCategory=categories.stream()
                .filter(cat->cat.getId() == breakfast.getId())
                .findFirst()
                .orElse(null);
        assertNotNull(promotedCategory);
        assertNull(promotedCategory.getParent()); //no parent in DB

        List<LedgerCategory> topLevelCategories=categories.stream()
                .filter(cat->cat.getParent() == null)
                .toList();
        assertTrue(topLevelCategories.stream().anyMatch(cat->cat.getId() == breakfast.getId())); //exists as top-level category in DB

        List<LedgerCategory> incomeCategories=topLevelCategories.stream()
                .filter(cat->cat.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(3, incomeCategories.size());

        List<LedgerCategory> expenseCategories=topLevelCategories.stream()
                .filter(cat->cat.getType() == CategoryType.EXPENSE)
                .toList();
        assertEquals(10, expenseCategories.size());

        System.out.println("Expense Categories:");
        for(LedgerCategory cat : expenseCategories){
            System.out.println(" Expense Category Name: "+cat.getName());
            for(LedgerCategory child : categories.stream()
                    .filter(c->c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("   Expense Child Name: "+child.getName());
            }
        }

        System.out.println("Income Categories:");
        for(LedgerCategory cat : incomeCategories){
            System.out.println(" Income Category Name: "+cat.getName());
            for(LedgerCategory child : categories.stream()
                    .filter(c->c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("   Income Child Name: "+child.getName());
            }
        }
    }

    @Test
    public void testPromoteSubCategory_Failure() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        assertFalse(ledgerCategoryController.promoteSubCategory(food)); //not a sub-category
        assertFalse(ledgerCategoryController.promoteSubCategory(null)); //null category
    }

    @Test
    public void testDemoteCategory_Success() {
        LedgerCategory bonus = testCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(bonus);
        assertNull(bonus.getParent());

        LedgerCategory salary=testCategories.stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        boolean result = ledgerCategoryController.demoteCategory(bonus, salary);
        assertTrue(result);
        assertEquals(salary.getId(), bonus.getParent().getId());

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedger(testLedger);
        LedgerCategory demotedCategory=categories.stream()
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

        List<LedgerCategory> expenseCategories=rootCategories.stream()
                .filter(cat->cat.getType() == CategoryType.EXPENSE)
                .toList();
        assertEquals(9, expenseCategories.size());

        System.out.println("Income Categories:");
        for(LedgerCategory cat : incomeCategories) {
            System.out.println(" Category Name: " + cat.getName());
            for (LedgerCategory child : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()) {
                System.out.println("   Child Name: " + child.getName());
            }
        }
        System.out.println("Expense Categories:");
        for(LedgerCategory cat : expenseCategories) {
            System.out.println(" Category Name: " + cat.getName());
            for (LedgerCategory child : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()) {
                System.out.println("   Child Name: " + child.getName());
            }
        }
    }

    @Test
    public void testDemoteCategory_Failure() {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);
        LedgerCategory salary=testCategories.stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        assertFalse(ledgerCategoryController.demoteCategory(shopping, salary)); //different types
        assertFalse(ledgerCategoryController.demoteCategory(shopping, null)); //parent is null
        assertFalse(ledgerCategoryController.demoteCategory(null, salary)); //category is null
        assertFalse(ledgerCategoryController.demoteCategory(shopping, shopping)); //same category

        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        assertFalse(ledgerCategoryController.demoteCategory(shopping, breakfast)); //parent is not top-level
        assertFalse(ledgerCategoryController.demoteCategory(breakfast, shopping)); //category is not top-level

        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        assertFalse(ledgerCategoryController.demoteCategory(food, shopping)); //category has children
    }

    @Test
    public void testChangeParent_Success() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        LedgerCategory entertainment = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        assertNotNull(entertainment);

        boolean result = ledgerCategoryController.changeParent(breakfast, entertainment);
        assertTrue(result);
        assertEquals(entertainment.getId(), breakfast.getParent().getId());

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedger(testLedger);
        LedgerCategory updatedCategory = categories.stream()
                .filter(cat -> cat.getId() == breakfast.getId())
                .findFirst()
                .orElse(null);
        assertNotNull(updatedCategory);
        assertEquals(entertainment.getId(), updatedCategory.getParent().getId()); //parent set in DB

        List<LedgerCategory> topLevelCategories = categories.stream()
                .filter(cat -> cat.getParent() == null)
                .toList();
        List<LedgerCategory> incomeCategories = topLevelCategories.stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .toList();
        assertEquals(3, incomeCategories.size());
        List<LedgerCategory> expenseCategories = topLevelCategories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .toList();
        assertEquals(9, expenseCategories.size());
        System.out.println("Income Categories:");
        for (LedgerCategory cat : incomeCategories) {
            System.out.println(" Category Name: " + cat.getName());
            for (LedgerCategory child : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()) {
                System.out.println("   Child Name: " + child.getName());
            }
        }
        System.out.println("Expense Categories:");
        for (LedgerCategory cat : expenseCategories) {
            System.out.println(" Category Name: " + cat.getName());
            for (LedgerCategory child : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()) {
                System.out.println("   Child Name: " + child.getName());
            }
        }
    }

    //category and new parent are the same
    @Test
    public void testChangeParent_Failure() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        assertFalse(ledgerCategoryController.changeParent(breakfast, breakfast)); //same category
        LedgerCategory salary = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);
        assertFalse(ledgerCategoryController.changeParent(breakfast, salary)); //different types

        LedgerCategory lunch = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);
        assertFalse(ledgerCategoryController.changeParent(breakfast, lunch)); //new parent is not top-level
        assertFalse(ledgerCategoryController.changeParent(breakfast, null)); //new parent is null
        assertFalse(ledgerCategoryController.changeParent(null, lunch)); //category is null

        LedgerCategory food = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        assertFalse(ledgerCategoryController.changeParent(food, lunch)); //category is not sub-category


    }

    //test LedgerCategory tree structure
    @Test
    public void testLedgerCategoryTreeStructure() {
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

        System.out.println("Expense Category Tree:");
        for (LedgerCategory category : expenseRootCategories) {
            System.out.println("Category ID: " + category.getId() +
                    ", Name: " + category.getName() +
                    ", Parent: " + (category.getParent() != null ? category.getParent().getName() : "null"));
            for (LedgerCategory subcategory : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList()) {
                System.out.println("  Subcategory ID: " + subcategory.getId() +
                        ", Name: " + subcategory.getName() +
                        ", Parent: " + subcategory.getParent().getName() );
            }
        }

        System.out.println("Income Category Tree:");
        for( LedgerCategory category : incomeRootCategories) {
            System.out.println("Category ID: " + category.getId() +
                    ", Name: " + category.getName() +
                    ", Parent: " + (category.getParent() != null ? category.getParent().getName() : "null"));
            for (LedgerCategory subcategory : categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == category.getId())
                    .toList()) {
                System.out.println("  Subcategory ID: " + subcategory.getId() +
                        ", Name: " + subcategory.getName() +
                        ", Parent: " +  subcategory.getParent().getName());
            }
        }
    }
}

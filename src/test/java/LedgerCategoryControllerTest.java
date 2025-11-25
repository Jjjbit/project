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

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);

        UserController userController = new UserController(userDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        ledgerCategoryController = new LedgerCategoryController(ledgerCategoryDAO, transactionDAO, budgetDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        AccountController accountController = new AccountController(accountDAO, transactionDAO);

        userController.register("test user", "password123");
        User testUser = userController.login("test user", "password123");

        testLedger=ledgerController.createLedger("Test Ledger", testUser);

        testCategories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());

        account =accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true,
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


    //create category of first-level
    @Test
    public void testCreateCategory_Success() {
        LedgerCategory category=ledgerCategoryController.createCategory("Test", testLedger, CategoryType.EXPENSE);
        assertNotNull(category);
        assertNotNull(ledgerCategoryDAO.getById(category.getId()));

        Budget monthlyBudget=budgetDAO.getBudgetByCategoryId(category.getId(), Budget.Period.MONTHLY);
        Budget yearlyBudget=budgetDAO.getBudgetByCategoryId(category.getId(), Budget.Period.YEARLY);
        assertNotNull(monthlyBudget);
        assertNotNull(yearlyBudget);
    }

    //create sub-category
    @Test
    public void testCreateSubCategory_Success() {
        LedgerCategory parentCategory=testCategories.stream()
                .filter(cat->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(parentCategory);

        LedgerCategory subCategory=ledgerCategoryController.createSubCategory("Test", parentCategory, testLedger);
        assertNotNull(subCategory); //created successfully
        assertNotNull(ledgerCategoryDAO.getById(subCategory.getId())); //exists in DB

        assertEquals(parentCategory.getId(), subCategory.getParent().getId());

        Budget monthlyBudget=budgetDAO.getBudgetByCategoryId(subCategory.getId(), Budget.Period.MONTHLY);
        Budget yearlyBudget=budgetDAO.getBudgetByCategoryId(subCategory.getId(), Budget.Period.YEARLY);
        assertNotNull(monthlyBudget);
        assertNotNull(yearlyBudget);

        List<LedgerCategory> categories=ledgerCategoryDAO.getCategoriesByParentId(parentCategory.getId());
        assertEquals(4, categories.size()); //exists in DB under parent
        assertTrue(categories.stream().anyMatch(cat->cat.getId() == subCategory.getId()));
    }

    //delete sub-category with budget
    @Test
    public void testDeleteLedgerCategory_WithBudget() {
        LedgerCategory breakfast=testCategories.stream()
                .filter(cat->cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        Budget budget=budgetDAO.getBudgetByCategoryId(breakfast.getId(), Budget.Period.MONTHLY);
        assertNotNull(budget);

        boolean result=ledgerCategoryController.deleteCategory(breakfast, true, null);
        assertTrue(result); //should succeed
        assertNull(budgetDAO.getById(budget.getId())); //budget should be deleted
    }

    //delete category with transactions
    @Test
    public void testDeleteLedgerCategory_DeleteTransactions() {
        LedgerCategory salary=testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Transaction tx1=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        boolean result = ledgerCategoryController.deleteCategory(salary, true, null);
        assertTrue(result);
        assertNull(ledgerCategoryDAO.getById(salary.getId())); //category deleted from DB
        assertNull(transactionDAO.getById(tx1.getId())); //transaction deleted from DB
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        assertEquals(16, categories.size()); //one category less in DB

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
    @Test
    public void testDeleteLedgerCategory_KeepTransactions() {
        LedgerCategory salary=testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);
        LedgerCategory bonus=testCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(bonus);

        Transaction tx1=transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        boolean result = ledgerCategoryController.deleteCategory(salary, false, bonus);
        assertTrue(result);
        assertNull(ledgerCategoryDAO.getById(salary.getId())); //salary deleted from DB
        assertNotNull(transactionDAO.getById(tx1.getId())); //transaction should exist in DB
        assertEquals(1, transactionDAO.getByCategoryId(bonus.getId()).size()); //transaction migrated to bonus
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(tx1.getId(), transactionDAO.getByCategoryId(bonus.getId()).getFirst().getId());
        assertEquals(tx1.getId(), transactionDAO.getByLedgerId(testLedger.getId()).getFirst().getId());

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        assertEquals(16, categories.size()); //one category less in DB

        List<LedgerCategory> parents=categories.stream()
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

    //delete category with sub-categories
    @Test
    public void testDeleteLedgerCategory_HasSubCategories_Failure() {
        LedgerCategory foodCategory = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(foodCategory);

        boolean result = ledgerCategoryController.deleteCategory(foodCategory, true, null);
        assertFalse(result); //should fail because it has sub-categories
        assertNotNull(ledgerCategoryDAO.getById(foodCategory.getId())); //category should still exist in DB
    }

    //rename category successfully
    @Test
    public void testRenameLedgerCategory_Success() {
        LedgerCategory foodCategory = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(foodCategory);

        boolean result = ledgerCategoryController.renameCategory(foodCategory, "Groceries", testLedger);
        assertTrue(result);
        assertEquals("Groceries", foodCategory.getName());

        LedgerCategory updatedCategory=ledgerCategoryDAO.getById(foodCategory.getId());
        assertEquals("Groceries", updatedCategory.getName());
    }

    @Test
    public void testRenameLedgerCategory_DuplicateName() {
        LedgerCategory foodCategory = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(foodCategory);

        boolean result = ledgerCategoryController.renameCategory(foodCategory, "Salary", testLedger);
        assertFalse(result);

        LedgerCategory updatedCategory=ledgerCategoryDAO.getById(foodCategory.getId());
        assertEquals("Food", updatedCategory.getName()); //name should remain unchanged
    }

    @Test
    public void testRenameLedgerCategory_NullCategory() {
        String newName = "New Category Name";
        boolean result = ledgerCategoryController.renameCategory(null, newName, testLedger);
        assertFalse(result);
    }

    @Test
    public void testRenameLedgerCategory_InvalidName() {
        LedgerCategory foodCategory = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(foodCategory);

        String invalidName = "   "; // empty after trim
        boolean result = ledgerCategoryController.renameCategory(foodCategory, invalidName, testLedger);
        assertFalse(result);
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

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
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

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
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

    //category and new parent have different types
    @Test
    public void testDemoteCategory_Failure_DifferentType() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        LedgerCategory salary=testCategories.stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        boolean result = ledgerCategoryController.demoteCategory(food, salary);
        assertFalse(result);
    }

    //category has children
    @Test
    public void testDemoteCategory_Failure_HasChildren() {
        LedgerCategory food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        LedgerCategory salary=testCategories.stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        boolean result = ledgerCategoryController.demoteCategory(food, salary);
        assertFalse(result);
    }

    //category and new parent are the same
    @Test
    public void testDemoteCategory_Failure_SameCategory() {
        LedgerCategory bonus = testCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(bonus);

        boolean result = ledgerCategoryController.demoteCategory(bonus, bonus);
        assertFalse(result);
    }

    //new parent is not top-level
    @Test
    public void testDemoteCategory_Failure_NonTopLevelCategory() {
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

        boolean result = ledgerCategoryController.demoteCategory(food, breakfast);
        assertFalse(result);
    }

    //category is null
    @Test
    public void testDemoteCategory_Failure_NullCategory() {
        LedgerCategory salary=testCategories.stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        boolean result = ledgerCategoryController.demoteCategory(null, salary);
        assertFalse(result);
    }

    //new parent is null
    @Test
    public void testDemoteCategory_Failure_NullNewParent() {
        LedgerCategory bonus = testCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(bonus);

        boolean result = ledgerCategoryController.demoteCategory(bonus, null);
        assertFalse(result);
    }

    //category is not top-level
    @Test
    public void testDemoteCategory_Failure_CategoryNotTopLevel() {
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

        boolean result = ledgerCategoryController.demoteCategory(breakfast, food);
        assertFalse(result);
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

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
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
    public void testChangeParent_Failure_SameCategory() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);

        boolean result = ledgerCategoryController.changeParent(breakfast, breakfast);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_DifferentType() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        LedgerCategory salary = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        boolean result = ledgerCategoryController.changeParent(breakfast, salary);
        assertFalse(result);
    }

    //new parent is not top-level
    @Test
    public void testChangeParent_Failure_NonTopLevelCategory() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast);
        LedgerCategory lunch = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(lunch);

        boolean result = ledgerCategoryController.changeParent(breakfast, lunch);
        assertFalse(result);
    }

    //null category to change parent
    @Test
    public void testChangeParent_Failure_NullCategory() {
        LedgerCategory entertainment = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        assertNotNull(entertainment);

        boolean result = ledgerCategoryController.changeParent(null, entertainment);
        assertFalse(result);
    }

    //null new parent
    @Test
    public void testChangeParent_Failure_NullNewParent() {
        LedgerCategory breakfast = testCategories.stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(breakfast, null);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_CategoryTopLevel() {
        LedgerCategory entertainment = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        LedgerCategory food = testCategories.stream()
                .filter((cat) -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(food, entertainment);
        assertFalse(result);
    }
}

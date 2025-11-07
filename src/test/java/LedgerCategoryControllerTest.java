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
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class LedgerCategoryControllerTest {
    private Connection connection;

    private Ledger testLedger;
    private User testUser;
    private Account account1;

    private LedgerCategoryController ledgerCategoryController;
    private LedgerController ledgerController;
    private UserController userController;
    private BudgetController budgetController;
    private TransactionController transactionController;
    private AccountController accountController;

    private LedgerCategoryDAO ledgerCategoryDAO;
    private UserDAO userDAO;
    private BudgetDAO budgetDAO;
    private LedgerDAO ledgerDAO;
    private CategoryDAO categoryDAO;
    private TransactionDAO transactionDAO;
    private AccountDAO accountDAO;
    private InstallmentDAO installmentDAO;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        budgetDAO = new BudgetDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        categoryDAO = new CategoryDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        accountDAO = new AccountDAO(connection);
        installmentDAO = new InstallmentDAO(connection);

        userController = new UserController(userDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        ledgerCategoryController = new LedgerCategoryController(ledgerCategoryDAO, ledgerDAO, transactionDAO, budgetDAO);
        budgetController = new BudgetController(budgetDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
        accountController = new AccountController(accountDAO, transactionDAO, installmentDAO);

        userController.register("testuser", "password123");
        testUser = userController.login("testuser", "password123");

        testLedger=ledgerController.createLedger("Test Ledger", testUser);
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
    public void testCreateCategory_Success() throws SQLException{
        LedgerCategory category=ledgerCategoryController.createCategory("Test", testLedger, CategoryType.EXPENSE);
        assertNotNull(category);
        assertNotNull(ledgerCategoryDAO.getById(category.getId()));
        assertEquals(2, category.getBudgets().size());
    }

    @Test
    public void testCreateSubCategory_Success() throws SQLException{
        LedgerCategory parentCategory=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        LedgerCategory subCategory=ledgerCategoryController.createSubCategory("Test", parentCategory);
        assertNotNull(subCategory); //created successfully
        assertEquals(parentCategory.getId(), subCategory.getParent().getId());
        assertEquals(4, parentCategory.getChildren().size());
        assertNotNull(ledgerCategoryDAO.getById(subCategory.getId())); //exists in DB
        assertEquals(2, subCategory.getBudgets().size());
    }

    @Test
    public void testDeleteLedgerCategory_WithBudget() throws SQLException{
        LedgerCategory breakfast=testLedger.getCategories().stream()
                .filter(cat->cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        Budget budget=breakfast.getBudgets().stream()
                .filter(bud->bud.getPeriod() == Budget.Period.MONTHLY)
                .findFirst()
                .orElse(null);

        boolean result=ledgerCategoryController.deleteCategory(breakfast, true, null);
        assertTrue(result); //should succeed
        assertNull(budgetDAO.getById(budget.getId())); //budget should be deleted
    }

    @Test
    public void testDeleteLedgerCategory_DeleteTransactions() throws SQLException {
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        Transaction tx1=transactionController.createIncome(testLedger, account1, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        boolean result = ledgerCategoryController.deleteCategory(salary, true, null);
        assertTrue(result);
        assertNull(ledgerCategoryDAO.getById(salary.getId())); //category deleted from DB
        assertNull(transactionDAO.getById(tx1.getId())); //transaction deleted from DB
        assertEquals(16, testLedger.getCategories().size()); //one category less in ledger
        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        assertEquals(16, categories.size()); //one category less in DB
    }

    @Test
    public void testDeleteLedgerCategory_KeepTransactions() throws SQLException {
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        LedgerCategory bonus=testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);

        Transaction tx1=transactionController.createIncome(testLedger, account1, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        boolean result = ledgerCategoryController.deleteCategory(salary, false, bonus);
        assertTrue(result);
        assertNull(ledgerCategoryDAO.getById(salary.getId())); //salary deleted from DB
        assertNotNull(transactionDAO.getById(tx1.getId())); //transaction should exist in DB
        assertEquals(16, testLedger.getCategories().size()); //one category less in ledger
        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        assertEquals(16, categories.size()); //one category less in DB
        assertEquals(bonus.getId(), tx1.getCategory().getId());
        assertEquals(1, bonus.getTransactions().size());
        List<Transaction> bonusTxs=transactionDAO.get(Map.of("category_id", bonus.getId()), null, null);
        assertEquals(1, bonusTxs.size()); //transaction migrated to bonus in DB

    }
    @Test
    public void testDeleteLedgerCategory_HasSubCategories_Failure() throws SQLException {
        LedgerCategory foodCategory = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.deleteCategory(foodCategory, true, null);
        assertFalse(result); //should fail because it has sub-categories
        assertNotNull(ledgerCategoryDAO.getById(foodCategory.getId())); //category should still exist in DB
    }

    @Test
    public void testRenameLedgerCategory_Success() throws SQLException {
        LedgerCategory foodCategory = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        String newName = "Groceries";
        boolean result = ledgerCategoryController.renameCategory(foodCategory, newName);
        assertTrue(result);
        assertEquals(newName, foodCategory.getName());
    }

    @Test
    public void testRenameLedgerCategory_DuplicateName() throws SQLException {
        LedgerCategory foodCategory = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        String duplicateName = "Salary";
        boolean result = ledgerCategoryController.renameCategory(foodCategory, duplicateName);
        assertFalse(result);
    }

    @Test
    public void testRenameLedgerCategory_NullCategory() throws SQLException {
        String newName = "New Category Name";
        boolean result = ledgerCategoryController.renameCategory(null, newName);
        assertFalse(result);
    }

    @Test
    public void testRenameLedgerCategory_InvalidName() throws SQLException {
        LedgerCategory foodCategory = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        String invalidName = "   "; // empty after trim
        boolean result = ledgerCategoryController.renameCategory(foodCategory, invalidName);
        assertFalse(result);
    }

    @Test
    public void testPromoteSubCategory_Success() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory food=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakfast.getParent());
        assertEquals(food.getId(), breakfast.getParent().getId());


        boolean result = ledgerCategoryController.promoteSubCategory(breakfast);
        assertTrue(result);
        assertNull(breakfast.getParent());
        assertEquals(2, food.getChildren().size()); //one less child in food category

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        LedgerCategory promotedCategory=categories.stream()
                .filter(cat->cat.getId().equals(breakfast.getId()))
                .findFirst()
                .orElse(null);
        assertNull(promotedCategory.getParent()); //no parent in DB
    }

    @Test
    public void testDemoteCategory_Success() throws SQLException {
        LedgerCategory bonus = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNull(bonus.getParent());

        boolean result = ledgerCategoryController.demoteCategory(bonus, salary);
        assertTrue(result);
        assertEquals(salary.getId(), bonus.getParent().getId());
        assertEquals(1, salary.getChildren().size()); //one more child in food category

        List<LedgerCategory> categories=ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        LedgerCategory demotedCategory=categories.stream()
                .filter(cat->cat.getId().equals(bonus.getId()))
                .findFirst()
                .orElse(null);
        assertEquals(salary.getId(), demotedCategory.getParent().getId()); //parent set in DB
    }

    //category and new parent have different types
    @Test
    public void testDemoteCategory_Failure_DifferentType() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(food, salary);
        assertFalse(result);
    }

    //category has children
    @Test
    public void testDemoteCategory_Failure_HasChildren() throws SQLException {
        LedgerCategory food = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(food, salary);
        assertFalse(result);
    }

    //category and new parent are the same
    @Test
    public void testDemoteCategory_Failure_SameCategory() throws SQLException {
        LedgerCategory bonus = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(bonus, bonus);
        assertFalse(result);
    }

    //new parent is not top-level
    @Test
    public void testDemoteCategory_Failure_NonTopLevelCategory() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory food=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(food, breakfast);
        assertFalse(result);
    }

    //category is null
    @Test
    public void testDemoteCategory_Failure_NullCategory() throws SQLException {
        LedgerCategory salary=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(null, salary);
        assertFalse(result);
    }

    //new parent is null
    @Test
    public void testDemoteCategory_Failure_NullNewParent() throws SQLException {
        LedgerCategory bonus = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(bonus, null);
        assertFalse(result);
    }

    //new parent belongs to different ledger
    @Test
    public void testDemoteCategory_Failure_DifferentLedgers() throws SQLException {
        LedgerCategory bonus = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        Ledger anotherLedger=ledgerController.createLedger("Another Ledger", testUser);
        LedgerCategory anotherLedgerCategory=ledgerCategoryController.createCategory("Another Category", anotherLedger, CategoryType.INCOME);

        boolean result = ledgerCategoryController.demoteCategory(bonus, anotherLedgerCategory);
        assertFalse(result);
    }

    //category is not top-level
    @Test
    public void testDemoteCategory_Failure_CategoryNotTopLevel() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory food=testLedger.getCategories().stream()
                .filter((cat)->cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.demoteCategory(breakfast, food);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Success() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(breakfast, entertainment);
        assertTrue(result);
        assertEquals(entertainment.getId(), breakfast.getParent().getId());
        assertEquals(1, entertainment.getChildren().size()); //one more child in entertainment category

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());
        LedgerCategory updatedCategory = categories.stream()
                .filter(cat -> cat.getId().equals(breakfast.getId()))
                .findFirst()
                .orElse(null);
        assertEquals(entertainment.getId(), updatedCategory.getParent().getId()); //parent set in DB
    }

    @Test
    public void testChangeParent_Failure_SameParent() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory food = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(food, food);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_DifferentLedgers() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        Ledger anotherLedger = ledgerController.createLedger("Another Ledger", testUser);
        LedgerCategory anotherLedgerCategory = ledgerCategoryController.createCategory("Another Category", anotherLedger, CategoryType.EXPENSE);

        boolean result = ledgerCategoryController.changeParent(breakfast, anotherLedgerCategory);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_DifferentType() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory salary = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(breakfast, salary);
        assertFalse(result);
    }

    //new parent is not top-level
    @Test
    public void testChangeParent_Failure_NonTopLevelCategory() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        LedgerCategory lunch = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Lunch"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(breakfast, lunch);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_NullCategory() throws SQLException {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(null, entertainment);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_NullNewParent() throws SQLException {
        LedgerCategory breakfast = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Breakfast"))
                .findFirst()
                .orElse(null);
        boolean result = ledgerCategoryController.changeParent(breakfast, null);
        assertFalse(result);
    }

    @Test
    public void testChangeParent_Failure_CategoryTopLevel() throws SQLException {
        LedgerCategory entertainment = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Entertainment"))
                .findFirst()
                .orElse(null);
        LedgerCategory food = testLedger.getCategories().stream()
                .filter((cat) -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        boolean result = ledgerCategoryController.changeParent(food, entertainment);
        assertFalse(result);
    }




}

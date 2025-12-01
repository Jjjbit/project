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

public class LedgerControllerTest {
    private Connection connection;

    private LedgerCategoryDAO ledgerCategoryDAO;
    private TransactionDAO transactionDAO;
    private LedgerDAO ledgerDAO;
    private BudgetDAO budgetDAO;
    private ReimbursementRecordDAO reimbursementRecordDAO;
    private ReimbursementDAO reimbursementDAO;
    private ReimbursementTxLinkDAO reimbursementTxLinkDAO;

    private LedgerController ledgerController;
    private TransactionController  transactionController;

    private User testUser;
    private Account account;

    @BeforeEach
    public void setUp() throws SQLException {
        connection=ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        AccountDAO accountDAO = new AccountDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
        transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        budgetDAO = new BudgetDAO(connection, ledgerCategoryDAO);
        reimbursementDAO = new ReimbursementDAO(connection, transactionDAO);
        reimbursementTxLinkDAO = new ReimbursementTxLinkDAO(connection, transactionDAO);
        reimbursementRecordDAO = new ReimbursementRecordDAO(connection, transactionDAO);

        UserController userController = new UserController(userDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, reimbursementRecordDAO, reimbursementDAO, reimbursementTxLinkDAO);
        AccountController accountController = new AccountController(accountDAO, transactionDAO);

        userController.register("test user", "password123"); // create test user and insert into db
        testUser = userController.login("test user", "password123"); // login to set current user

        account = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000.00),
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

    @Test
    public void testCreateLedger() {
        Ledger ledger = ledgerController.createLedger("Test Ledger", testUser);
        assertNotNull(ledger);

        assertNotNull(ledgerDAO.getById(ledger.getId()));
        assertEquals(1, ledgerDAO.getLedgersByUserId(testUser.getId()).size());

        Budget monthlyLedgerBudget = budgetDAO.getBudgetByLedgerId(ledger.getId(), Budget.Period.MONTHLY);
        Budget yearlyLedgerBudget = budgetDAO.getBudgetByLedgerId(ledger.getId(), Budget.Period.YEARLY);
        assertNotNull(monthlyLedgerBudget);
        assertNotNull(yearlyLedgerBudget);

        assertEquals(17, ledgerCategoryDAO.getTreeByLedgerId(ledger.getId()).size());
        List<LedgerCategory> categories= ledgerCategoryDAO.getTreeByLedgerId(ledger.getId());

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
            Budget monthlyBudget = budgetDAO.getBudgetByCategoryId(cat.getId(), Budget.Period.MONTHLY);
            Budget yearlyBudget = budgetDAO.getBudgetByCategoryId(cat.getId(), Budget.Period.YEARLY);
            assertNotNull(monthlyBudget);
            assertNotNull(yearlyBudget);
            System.out.println(" Monthly Budget: " + monthlyBudget.getAmount());
            System.out.println(" Yearly Budget: " + yearlyBudget.getAmount());

            for(LedgerCategory sub: categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("  Expense Subcategory: " + sub.getName());
                Budget subMonthlyBudget = budgetDAO.getBudgetByCategoryId(sub.getId(), Budget.Period.MONTHLY);
                Budget subYearlyBudget = budgetDAO.getBudgetByCategoryId(sub.getId(), Budget.Period.YEARLY);
                assertNotNull(subMonthlyBudget);
                assertNotNull(subYearlyBudget);
                System.out.println("  Monthly Budget: " + subMonthlyBudget.getAmount());
                System.out.println("  Yearly Budget: " + subYearlyBudget.getAmount());
            }
        }

        System.out.println("Income Categories:");
        for(LedgerCategory cat : income){
            System.out.println(" Income Category: " + cat.getName());
            Budget monthlyBudget = budgetDAO.getBudgetByCategoryId(cat.getId(), Budget.Period.MONTHLY);
            Budget yearlyBudget = budgetDAO.getBudgetByCategoryId(cat.getId(), Budget.Period.YEARLY);
            assertNull(monthlyBudget);
            assertNull(yearlyBudget);
            for(LedgerCategory sub: categories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == cat.getId())
                    .toList()){
                System.out.println("   Income Subcategory: " + sub.getName());
                Budget subMonthlyBudget = budgetDAO.getBudgetByCategoryId(sub.getId(), Budget.Period.MONTHLY);
                Budget subYearlyBudget = budgetDAO.getBudgetByCategoryId(sub.getId(), Budget.Period.YEARLY);
                assertNull(subMonthlyBudget);
                assertNull(subYearlyBudget);
            }
        }
    }

    @Test
    public void testCreateLedger_DuplicateName_Failure() {
        Ledger ledger = ledgerController.createLedger("Duplicate Ledger", testUser);
        assertNotNull(ledger);

        assertNull(ledgerController.createLedger("Duplicate Ledger", testUser));
    }

    @Test
    public void testDeleteLedger_NullTransaction() {
        Ledger ledger = ledgerController.createLedger("Ledger To Delete", testUser);
        assertNotNull(ledger);

        boolean deleted = ledgerController.deleteLedger(ledger);
        assertTrue(deleted);

        assertNull(ledgerDAO.getById(ledger.getId()));
        assertEquals(0, ledgerDAO.getLedgersByUserId(testUser.getId()).size());
        assertEquals(0, ledgerCategoryDAO.getTreeByLedgerId(ledger.getId()).size());
    }

    @Test
    public void testDeleteLedger_WithTransactions() {
        Ledger ledger = ledgerController.createLedger("Ledger With Transactions", testUser);
        assertNotNull(ledger);

        List<LedgerCategory> categories = ledgerCategoryDAO.getTreeByLedgerId(ledger.getId());
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

        List<Long> categoryIds = categories.stream()
                .map(LedgerCategory::getId)
                .toList();

        //create transactions
        Transaction tx1=transactionController.createExpense(ledger, account, food, null, LocalDate.now(), BigDecimal.valueOf(10.00), false);
        Transaction tx2=transactionController.createIncome(ledger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000.00));

        //delete ledger
        boolean deleted = ledgerController.deleteLedger(ledger);
        assertTrue(deleted);
        assertNull(ledgerDAO.getById(ledger.getId()));
        assertNull(transactionDAO.getById(tx1.getId()));
        assertNull(transactionDAO.getById(tx2.getId()));
        assertEquals(0, transactionDAO.getByCategoryId(food.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, ledgerCategoryDAO.getTreeByLedgerId(ledger.getId()).size()); //all categories should be deleted
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size()); //transactions should be deleted

        //delete budgets
        assertNull(budgetDAO.getBudgetByLedgerId(ledger.getId(), Budget.Period.MONTHLY));
        assertNull(budgetDAO.getBudgetByLedgerId(ledger.getId(), Budget.Period.YEARLY));

        for(Long catId : categoryIds){
            assertNull(ledgerCategoryDAO.getById(catId));
            assertNull(budgetDAO.getBudgetByCategoryId(catId, Budget.Period.MONTHLY));
            assertNull(budgetDAO.getBudgetByCategoryId(catId, Budget.Period.YEARLY));
        }
    }

    @Test
    public void testCopyLedger() {
        Ledger originalLedger = ledgerController.createLedger("Original Ledger", testUser);
        assertNotNull(originalLedger);

        Ledger copiedLedger = ledgerController.copyLedger(originalLedger, testUser);
        assertNotNull(copiedLedger);

        Ledger fetchedCopiedLedger = ledgerDAO.getById(copiedLedger.getId());
        assertNotNull(fetchedCopiedLedger);
        assertEquals(copiedLedger.getName(), fetchedCopiedLedger.getName());

        assertEquals(2, ledgerDAO.getLedgersByUserId(testUser.getId()).size());
        assertNotNull(budgetDAO.getBudgetByLedgerId(copiedLedger.getId(), Budget.Period.MONTHLY));
        assertNotNull(budgetDAO.getBudgetByLedgerId(copiedLedger.getId(), Budget.Period.YEARLY));
        assertEquals(17, ledgerCategoryDAO.getTreeByLedgerId(copiedLedger.getId()).size());

        List<LedgerCategory> originalCategories = ledgerCategoryDAO.getTreeByLedgerId(copiedLedger.getId());
        List<LedgerCategory> copiedCategories = ledgerCategoryDAO.getTreeByLedgerId(copiedLedger.getId());
        assertEquals(originalCategories.size(), copiedCategories.size());

        List<LedgerCategory> rootCategories = copiedCategories.stream()
                .filter(cat -> cat.getParent() == null)
                .toList();

        List<LedgerCategory> incomeCategories = rootCategories.stream()
                .filter(cat -> cat.getType() == CategoryType.INCOME)
                .toList();
        List<LedgerCategory> expenseCategories = rootCategories.stream()
                .filter(cat -> cat.getType() == CategoryType.EXPENSE)
                .toList();

        for(LedgerCategory incomeCat : incomeCategories) {
            System.out.println("Income Category: " + incomeCat.getName());
            assertNull(budgetDAO.getBudgetByCategoryId(incomeCat.getId(), Budget.Period.MONTHLY));
            assertNull(budgetDAO.getBudgetByCategoryId(incomeCat.getId(), Budget.Period.YEARLY));
            for(LedgerCategory subCat : copiedCategories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == incomeCat.getId())
                    .toList()) {
                System.out.println("  Subcategory: " + subCat.getName());
                assertNull(budgetDAO.getBudgetByCategoryId(subCat.getId(), Budget.Period.MONTHLY));
                assertNull(budgetDAO.getBudgetByCategoryId(subCat.getId(), Budget.Period.YEARLY));
            }
        }

        for(LedgerCategory expenseCat : expenseCategories) {
            System.out.println("Expense Category: " + expenseCat.getName());
            Budget monthlyBudget = budgetDAO.getBudgetByCategoryId(expenseCat.getId(), Budget.Period.MONTHLY);
            Budget yearlyBudget = budgetDAO.getBudgetByCategoryId(expenseCat.getId(), Budget.Period.YEARLY);
            assertNotNull(monthlyBudget);
            assertNotNull(yearlyBudget);
            System.out.println(" Monthly Budget: " + monthlyBudget.getAmount());
            System.out.println(" Yearly Budget: " + yearlyBudget.getAmount());
            for(LedgerCategory subCat : copiedCategories.stream()
                    .filter(c -> c.getParent() != null && c.getParent().getId() == expenseCat.getId())
                    .toList()) {
                System.out.println("  Subcategory: " + subCat.getName());
                Budget subMonthlyBudget = budgetDAO.getBudgetByCategoryId(subCat.getId(), Budget.Period.MONTHLY);
                Budget subYearlyBudget = budgetDAO.getBudgetByCategoryId(subCat.getId(), Budget.Period.YEARLY);
                assertNotNull(subMonthlyBudget);
                assertNotNull(subYearlyBudget);
                System.out.println("  Monthly Budget: " + subMonthlyBudget.getAmount());
                System.out.println("  Yearly Budget: " + subYearlyBudget.getAmount());
            }
        }
    }

    @Test
    public void testRenameLedger_Success() {
        Ledger ledger = ledgerController.createLedger("Ledger To Rename", testUser);
        assertNotNull(ledger);

        boolean renamed = ledgerController.renameLedger(ledger, "Renamed Ledger", testUser);
        assertTrue(renamed);
        Ledger updatedLedger = ledgerDAO.getById(ledger.getId());
        assertEquals("Renamed Ledger", updatedLedger.getName());
    }

    @Test
    public void testRenameLedger_DuplicateName_Failure() {
        Ledger ledger1 = ledgerController.createLedger("Ledger One", testUser);
        assertNotNull(ledger1);
        Ledger ledger2 = ledgerController.createLedger("Ledger Two", testUser);
        assertNotNull(ledger2);

        boolean renamed = ledgerController.renameLedger(ledger2, "Ledger One", testUser);
        assertFalse(renamed);
    }

    @Test
    public void testRenameLedger_SameName() {
        Ledger ledger = ledgerController.createLedger("Ledger Same Name", testUser);
        assertNotNull(ledger);

        boolean renamed = ledgerController.renameLedger(ledger, "Ledger Same Name", testUser);
        assertTrue(renamed);
    }

    @Test
    public void testRenameLedger_NullName_Failure() {
        Ledger ledger = ledgerController.createLedger("Ledger Null Name", testUser);
        assertNotNull(ledger);

        boolean renamed = ledgerController.renameLedger(ledger, "", testUser);
        assertFalse(renamed);
    }

    //test getLedger
    @Test
    public void testGetLedgersByUser() {
        //visible ledger is testLedger created in setup
        //create second ledger
        Ledger secondLedger = ledgerController.createLedger("Second Ledger", testUser);
        assertNotNull(secondLedger);

        //create ledger and delete it
        Ledger deletedLedger = ledgerController.createLedger("Deleted Ledger", testUser);
        assertNotNull(deletedLedger);
        ledgerController.deleteLedger(deletedLedger);

        List<Ledger> ledgers = ledgerController.getLedgersByUser(testUser);
        assertEquals(2, ledgers.size());
    }
}

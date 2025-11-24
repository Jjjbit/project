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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionControllerTest {
    private Connection connection;

    private User testUser;
    private Ledger testLedger;
    private BasicAccount testAccount;
    private List<LedgerCategory> testCategories;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;

    private TransactionController transactionController;
    private LedgerController ledgerController;
    private AccountController accountController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        accountDAO = new AccountDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        accountController = new AccountController(accountDAO, transactionDAO);

        userController.register("test user", "password123");
        testUser = userController.login("test user", "password123");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

        testCategories = ledgerCategoryDAO.getTreeByLedgerId(testLedger.getId());

        testAccount = accountController.createBasicAccount("Test Account", BigDecimal.valueOf(1000.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
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
    public void testCreateIncome_Success() throws SQLException {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Transaction income=transactionController.createIncome(testLedger, testAccount, salary,
                "June Salary",
                LocalDate.of(2024,6,30),
                BigDecimal.valueOf(5000.00));

        assertNotNull(income);
        assertNotNull(transactionDAO.getById(income.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedIncome = transactionDAO.getById(income.getId());
        assertEquals("June Salary", fetchedIncome.getNote());
        assertEquals(0, fetchedIncome.getAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(LocalDate.of(2024,6,30), fetchedIncome.getDate());
        assertEquals(testAccount.getId(), fetchedIncome.getToAccount().getId());
        assertNull(fetchedIncome.getFromAccount());
        assertEquals(salary.getId(), fetchedIncome.getCategory().getId());
        assertEquals(TransactionType.INCOME, fetchedIncome.getType());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00))); //1000 + 5000 = 6000
    }

    @Test
    public void testCreateExpense_Success() throws SQLException {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Transaction expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping",
                LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        assertNotNull(expense);
        assertNotNull(transactionDAO.getById(expense.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(shopping.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedExpense = transactionDAO.getById(expense.getId());
        assertEquals("Grocery Shopping", fetchedExpense.getNote());
        assertEquals(0, fetchedExpense.getAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(LocalDate.of(2024,6,25), fetchedExpense.getDate());
        assertEquals(testAccount.getId(), fetchedExpense.getFromAccount().getId());
        assertNull(fetchedExpense.getToAccount());
        assertEquals(shopping.getId(), fetchedExpense.getCategory().getId());
        assertEquals(TransactionType.EXPENSE, fetchedExpense.getType());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00))); //1000 - 150 = 850
    }

    @Test
    public void testCreateTransfer_Success() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser,
                "Savings Account Notes", true, true);

        Transaction transfer=transactionController.createTransfer(testLedger, testAccount, toAccount,
                "Transfer to Savings", LocalDate.of(2024,6,20),
                BigDecimal.valueOf(200.00));
        assertNotNull(transfer);
        assertNotNull(transactionDAO.getById(transfer.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(toAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Transfer to Savings", fetchedTransfer.getNote());
        assertEquals(0, fetchedTransfer.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024,6,20), fetchedTransfer.getDate());
        assertEquals(testAccount.getId(), fetchedTransfer.getFromAccount().getId());
        assertEquals(toAccount.getId(), fetchedTransfer.getToAccount().getId());
        assertNull(fetchedTransfer.getCategory());
        assertEquals(TransactionType.TRANSFER, fetchedTransfer.getType());

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //1000 - 200 = 800

        //verify toAccount balance updated
        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500 + 200 = 700
    }

    //delete
    @Test
    public void testDeleteIncome_Success() throws SQLException {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Transaction income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));
        assertNotNull(income);

        boolean deleted=transactionController.deleteTransaction(income);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(income.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteExpense_Success() throws SQLException {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Transaction expense=transactionController.createExpense(testLedger, testAccount, shopping, "Grocery Shopping",
                LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00));
        assertNotNull(expense);

        boolean deleted=transactionController.deleteTransaction(expense);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(expense.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(shopping.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteTransfer_Success() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account", BigDecimal.valueOf(500.00),
                AccountType.DEBIT_CARD, AccountCategory.FUNDS, testUser, "Savings Account Notes",
                true, true);

        Transaction transfer=transactionController.createTransfer(testLedger, testAccount, toAccount,"Transfer to Savings",
                LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00));
        assertNotNull(transfer);

        boolean deleted=transactionController.deleteTransaction(transfer);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(transfer.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(toAccount.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //verify toAccount balance updated
        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(500.00)));
    }

    //edit
    @Test
    public void testEditIncome_Success() throws SQLException {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        Account newAccount = accountController.createBasicAccount("New Account", BigDecimal.valueOf(2000.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "New Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedgerId(newLedger.getId());

        LedgerCategory newCategory = newLedgerCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(newCategory);

        boolean result=transactionController.updateIncome(income, newAccount,
                newCategory, "Updated June Salary", LocalDate.of(2024,6,30),
                BigDecimal.valueOf(6000.00), newLedger);
        assertTrue(result);

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());

        BasicAccount updatedNewAccount = (BasicAccount) accountDAO.getAccountById(newAccount.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(8000.00))); //2000 + 6000 = 8000
        assertEquals(1, transactionDAO.getByAccountId(newAccount.getId()).size());

        assertEquals(1, transactionDAO.getByLedgerId(newLedger.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(newCategory.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertEquals("Updated June Salary", updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(LocalDate.of(2024,6,30), updatedIncome.getDate());
        assertEquals(newAccount.getId(), updatedIncome.getToAccount().getId());
        assertNull(updatedIncome.getFromAccount());
        assertEquals(newCategory.getId(), updatedIncome.getCategory().getId());
        assertEquals(newLedger.getId(), updatedIncome.getLedger().getId());
    }

    /*@Test
    public void testEditIncome_NewFromAccount_Failure() throws SQLException {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        Account newFromAccount = accountController.createBasicAccount("New From Account", BigDecimal.valueOf(2000.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "New From Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedgerId(newLedger.getId());

        LedgerCategory newCategory = newLedgerCategories.stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);
        assertNotNull(newCategory);

        boolean result=transactionController.updateTransaction(income, newFromAccount, null, newCategory,
                "Updated June Salary", LocalDate.of(2024,6,30),
                BigDecimal.valueOf(6000.00), newLedger);
        assertFalse(result);
    }*/

    @Test
    public void testEditIncome_Invariant_Success() throws SQLException {
        LedgerCategory salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(salary);

        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary",
                LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        boolean result=transactionController.updateIncome(income, null, null, null,
                null, null, null);
        assertTrue(result);

        //verify account balance unchanged
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());

        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertNull(updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(LocalDate.of(2024,6,30), updatedIncome.getDate());
        assertEquals(testAccount.getId(), updatedIncome.getToAccount().getId());
    }

    @Test
    public void testEditExpense_Success() throws SQLException {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        Account newAccount = accountController.createBasicAccount("New Account", BigDecimal.valueOf(500.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "New Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedgerId(newLedger.getId());

        LedgerCategory newCategory = newLedgerCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        assertNotNull(newCategory);

        boolean result=transactionController.updateExpense(expense, newAccount,
                newCategory, "Updated Grocery Shopping", LocalDate.of(2024,6,26),
                BigDecimal.valueOf(200.00), newLedger);
        assertTrue(result);

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(newAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(newLedger.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(newCategory.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(shopping.getId()).size());

        BasicAccount updatedNewAccount = (BasicAccount) accountDAO.getAccountById(newAccount.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(300.00))); //500 - 200 = 300

        Transaction updatedExpense = transactionDAO.getById(expense.getId());
        assertEquals("Updated Grocery Shopping", updatedExpense.getNote());
        assertEquals(0, updatedExpense.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024,6,26), updatedExpense.getDate());
        assertEquals(newAccount.getId(), updatedExpense.getFromAccount().getId());
        assertNull(updatedExpense.getToAccount());
        assertEquals(newCategory.getId(), updatedExpense.getCategory().getId());
        assertEquals(newLedger.getId(), updatedExpense.getLedger().getId());
    }

    @Test
    public void testEditExpense_Invariant() throws SQLException {
        LedgerCategory shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        assertNotNull(shopping);

        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        boolean result=transactionController.updateExpense(expense, null,
                null, null, null, null, null);
        assertTrue(result);

        //verify account balance unchanged
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00)));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());

        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(shopping.getId()).size());

        Transaction updatedExpense = transactionDAO.getById(expense.getId());
        assertNull(updatedExpense.getNote());
        assertEquals(0, updatedExpense.getAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(LocalDate.of(2024,6,25), updatedExpense.getDate());
        assertEquals(testAccount.getId(), updatedExpense.getFromAccount().getId());
    }

    @Test
    public void testEditTransfer_Success() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, "Savings Account Notes", true, true);

        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                toAccount, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);

        boolean result = transactionController.updateTransfer(transfer, toAccount, testAccount,
                "Updated Transfer", LocalDate.of(2024, 6, 21),
                BigDecimal.valueOf(250.00), newLedger);
        assertTrue(result);

        //verify account balances updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1250.00))); //1000-200+200+250=1250

        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(250.00))); //500+200-200-250=250

        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Updated Transfer", updatedTransfer.getNote());
        assertEquals(0, updatedTransfer.getAmount().compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(LocalDate.of(2024, 6, 21), updatedTransfer.getDate());
        assertEquals(toAccount.getId(), updatedTransfer.getFromAccount().getId());
        assertEquals(testAccount.getId(), updatedTransfer.getToAccount().getId());
        assertEquals(newLedger.getId(), updatedTransfer.getLedger().getId());
    }

    //test edit transfer invariant
    //test edit transfer: old note is not null and new note is null
    //test edit transfer: old fromAccount is null and new fromAccount is not null
    //test edit transfer: old fromAccount is not null and new fromAccount is null
}


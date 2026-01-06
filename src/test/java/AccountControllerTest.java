import com.ledger.BusinessLogic.AccountController;
import com.ledger.BusinessLogic.LedgerController;
import com.ledger.BusinessLogic.TransactionController;
import com.ledger.BusinessLogic.UserController;
import com.ledger.DomainModel.*;
import com.ledger.ORM.*;
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


public class AccountControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private LedgerCategory salary;
    private LedgerCategory food;

    private AccountController accountController;
    private TransactionController transactionController;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;

    @BeforeEach
    public void setUp() {
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);

        userController.register("test user", "password123"); // create test user and insert into db
        testUser=userController.login("test user", "password123"); // login to set current user

        testLedger=ledgerController.createLedger("Test Ledger");

        List<LedgerCategory> testCategories = ledgerCategoryDAO.getTreeByLedger(testLedger);
        salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
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
    public void testCreateAccount() {
        Account account = accountController.createAccount("Alice's Savings", BigDecimal.valueOf(5000), true, true);
        assertNotNull(account);
        assertNotNull(accountDAO.getAccountById(account.getId()));
        assertEquals(1,  accountDAO.getAccountsByOwner(testUser).size());
    }

    @Test
    public void testCreateAccount_Failure() {
        assertNull(accountController.createAccount(null, BigDecimal.valueOf(1000), true, true)); //null name
        assertNull(accountController.createAccount("", BigDecimal.valueOf(1000), true, true)); //empty name
        assertNull(accountController.createAccount("a".repeat(51), null, true, true));
    }

    @Test
    public void testEditAccount_Success() {
        Account account = accountController.createAccount("Old Account Name", BigDecimal.valueOf(1200), true, true);
        assertTrue(accountController.editAccount(account, "New Account Name", BigDecimal.valueOf(1500), false, false));

        Account editedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("New Account Name", editedAccount.getName());
        assertEquals(0, editedAccount.getBalance().compareTo(BigDecimal.valueOf(1500)));
        assertFalse(editedAccount.getIncludedInAsset());
        assertFalse(editedAccount.getSelectable());
    }

    @Test
    public void testEditAccount_Failure() {
        Account account = accountController.createAccount("Account Name", BigDecimal.valueOf(1200), true, true);
        assertFalse(accountController.editAccount(account, null, BigDecimal.valueOf(1500), false, false));
        assertFalse(accountController.editAccount(account, "", BigDecimal.valueOf(1500), false, false));
        assertFalse(accountController.editAccount(account, "a".repeat(51), BigDecimal.valueOf(1500), false, false));
        assertFalse(accountController.editAccount(account, "Valid Name", null, false, false));
    }

    @Test
    public void testDeleteAccount_Success() {
        Account account = accountController.createAccount("Account to Delete", BigDecimal.valueOf(800), true, true);
        assertNotNull(account);
        Account toAccount = accountController.createAccount("To Account", BigDecimal.valueOf(500), true, true);
        assertNotNull(toAccount);

        Income income = transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000));
        assertNotNull(income);
        Expense expense = transactionController.createExpense(testLedger, account, food, null, LocalDate.now(), BigDecimal.valueOf(200));
        assertNotNull(expense);
        Transfer tx1 = transactionController.createTransfer(testLedger, account, toAccount, null, LocalDate.now(), BigDecimal.valueOf(100));
        assertNotNull(tx1);
        Transfer tx2 = transactionController.createTransfer(testLedger, null, account, null, LocalDate.now(), BigDecimal.valueOf(50));
        assertNotNull(tx2);
        Transfer tx3 = transactionController.createTransfer(testLedger, account, null, null, LocalDate.now(), BigDecimal.valueOf(25));
        assertNotNull(tx3);

        assertTrue(accountController.deleteAccount(account));
        assertNull(accountDAO.getAccountById(account.getId()));
        assertEquals(1, transactionDAO.getByAccountId(toAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        assertTrue(accountController.deleteAccount(toAccount));
        assertNull(accountDAO.getAccountById(toAccount.getId()));
        assertEquals(0, transactionDAO.getByAccountId(toAccount.getId()).size());
    }

    //test getSelectableAccounts and getAccounts
    @Test
    public void testGet() {
        Account testAccount = accountController.createAccount("Test Account", BigDecimal.valueOf(1000), true, true);
        assertNotNull(testAccount);
        Account testAccount2 = accountController.createAccount("Test Account 2", BigDecimal.valueOf(2000), true, false); //not selectable
        assertNotNull(testAccount2);
        assertEquals(1, accountController.getSelectableAccounts(testUser).size());
        assertEquals(2, accountController.getAccounts(testUser).size());
        accountController.editAccount(testAccount, "Test Account", BigDecimal.valueOf(1000), true, false); //make not selectable
        assertEquals(0, accountController.getSelectableAccounts(testUser).size());
        assertEquals(2, accountController.getAccounts(testUser).size());
    }

}
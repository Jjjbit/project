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


public class AccountControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private List<LedgerCategory> testCategories;
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

        testCategories = ledgerCategoryDAO.getTreeByLedger(testLedger);
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

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(5000.00)));
        assertTrue(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInAsset());

        List<Account> userAccounts = accountDAO.getAccountsByOwner(testUser);
        assertEquals(1, userAccounts.size());
        assertEquals(savedAccount.getId(), userAccounts.getFirst().getId());
    }

    @Test
    public void testCreateAccount_Failure() {
        Account account = accountController.createAccount(null, BigDecimal.valueOf(1000), true, true);
        assertNull(account);

        account = accountController.createAccount("", BigDecimal.valueOf(1000), true, true);
        assertNull(account);
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
    public void testDeleteAccount_Success() {
        Account account = accountController.createAccount("Account to Delete", BigDecimal.valueOf(800), true, true);
        assertNotNull(account);
        Account toAccount = accountController.createAccount("To Account", BigDecimal.valueOf(500), true, true);
        assertNotNull(toAccount);

        Income income = transactionController.createIncome(testLedger, account, salary, null, LocalDate.now(), BigDecimal.valueOf(1000));
        assertNotNull(income);
        Expense expense = transactionController.createExpense(testLedger, account, food, null, LocalDate.now(), BigDecimal.valueOf(200));
        assertNotNull(expense);
        Transfer transfer = transactionController.createTransfer(testLedger, account, toAccount, null, LocalDate.now(), BigDecimal.valueOf(100));
        assertNotNull(transfer);

        assertTrue(accountController.deleteAccount(account));
        assertNull(accountDAO.getAccountById(account.getId()));
        assertEquals(1, transactionDAO.getByAccountId(toAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(account.getId()).size());

        assertTrue(accountController.deleteAccount(toAccount));
        assertNull(accountDAO.getAccountById(toAccount.getId()));
        assertEquals(0, transactionDAO.getByAccountId(toAccount.getId()).size());
    }

    //test getVisibleAccount, getSelectableAccounts, getVisibleBorrowingAccounts,
    // getVisibleLendingAccounts, getCreditCardAccounts, getVisibleLoanAccounts
    @Test
    public void testGetAccounts() {
        Account testAccount = accountController.createAccount("Test Account",
                BigDecimal.valueOf(1000), true, true); //visible
        assertNotNull(testAccount);
        Account testAccount2 = accountController.createAccount("Test Account 2",
                BigDecimal.valueOf(2000), true, false); //not selectable
        assertNotNull(testAccount2);

        assertEquals(1, accountController.getSelectableAccounts(testUser).size());
        assertEquals(2, accountController.getAccounts(testUser).size());
//        assertEquals(2, accountController.getBorrowingAccounts(testUser).size());
//        assertEquals(2, accountController.getLendingAccounts(testUser).size());
//        assertEquals(2, accountController.getCreditCardAccounts(testUser).size());
//        assertEquals(2, accountController.getLoanAccounts(testUser).size());
    }

}
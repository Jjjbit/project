import com.ledger.business.AccountController;
import com.ledger.business.LedgerController;
import com.ledger.business.TransactionController;
import com.ledger.business.UserController;
import com.ledger.domain.*;
import com.ledger.orm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

public class TransactionControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private BasicAccount testAccount;

    private UserDAO userDAO;
    private LedgerDAO ledgerDAO;
    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;
    private CategoryDAO categoryDAO;

    private UserController userController;
    private TransactionController transactionController;
    private LedgerController ledgerController;
    private AccountController accountController;

    @BeforeEach
    public void setUp() throws SQLException {
        connection = ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        userDAO = new UserDAO(connection);
        ledgerDAO = new LedgerDAO(connection);
        accountDAO = new AccountDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        categoryDAO = new CategoryDAO(connection);

        userController = new UserController(userDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO);
        accountController = new AccountController(accountDAO, transactionDAO);

        userController.register("testuser", "password123");
        testUser = userController.login("testuser", "password123");

        testLedger = ledgerController.createLedger("Test Ledger", testUser);

        testAccount = accountController.createBasicAccount(
                "Test Account",
                BigDecimal.valueOf(1000.00),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                "Test Account Notes",
                true, true);

    }

    @AfterEach
    public void tearDown() throws SQLException {
        readResetScript();
    }

    private void runSchemaScript() {
        try {
            Path path = Paths.get("src/test/resources/schema.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema.sql", e);
        }
    }

    private void readResetScript() throws SQLException {
        try {
            Path path = Paths.get("src/test/resources/reset.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read reset.sql", e);
        }
    }

    //create
    @Test
    public void testCreateIncome_Success() throws SQLException {
        LedgerCategory category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        Transaction income=transactionController.createIncome(
                testLedger,
                testAccount,
                category,
                "June Salary",
                LocalDate.of(2024,6,30),
                BigDecimal.valueOf(5000.00)
        );
        assertNotNull(income);
        assertNotNull(transactionDAO.getById(income.getId()));

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00)));
    }

    @Test
    public void testCreateExpense_Success() throws SQLException {
        LedgerCategory category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        Transaction expense=transactionController.createExpense(
                testLedger,
                testAccount,
                category,
                "Grocery Shopping",
                LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00)
        );
        assertNotNull(expense);
        assertNotNull(transactionDAO.getById(expense.getId()));

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00)));
    }

    @Test
    public void testCreateTransfer_Success() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount(
                "Savings Account",
                BigDecimal.valueOf(500.00),
                AccountType.DEBIT_CARD,
                AccountCategory.FUNDS,
                testUser,
                "Savings Account Notes",
                true, true);
        Transaction transfer=transactionController.createTransfer(
                testLedger,
                testAccount,
                toAccount,
                "Transfer to Savings",
                LocalDate.of(2024,6,20),
                BigDecimal.valueOf(200.00)
        );
        assertNotNull(transfer);
        assertNotNull(transactionDAO.getById(transfer.getId()));

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        //verify toAccount balance updated
        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));
    }

    //delete
    @Test
    public void testDeleteIncome_Success() throws SQLException {
        LedgerCategory category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        Transaction income=transactionController.createIncome(
                testLedger,
                testAccount,
                category,
                "June Salary",
                LocalDate.of(2024,6,30),
                BigDecimal.valueOf(5000.00)
        );
        assertNotNull(income);

        boolean deleted=transactionController.deleteTransaction(income);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(income.getId()));

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteExpense_Success() throws SQLException {
        LedgerCategory category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        Transaction expense=transactionController.createExpense(
                testLedger,
                testAccount,
                category,
                "Grocery Shopping",
                LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00)
        );
        assertNotNull(expense);

        boolean deleted=transactionController.deleteTransaction(expense);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(expense.getId()));

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

    }

    @Test
    public void testDeleteTransfer_Success() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount(
                "Savings Account",
                BigDecimal.valueOf(500.00),
                AccountType.DEBIT_CARD,
                AccountCategory.FUNDS,
                testUser,
                "Savings Account Notes",
                true, true);
        Transaction transfer=transactionController.createTransfer(
                testLedger,
                testAccount,
                toAccount,
                "Transfer to Savings",
                LocalDate.of(2024,6,20),
                BigDecimal.valueOf(200.00)
        );
        assertNotNull(transfer);

        boolean deleted=transactionController.deleteTransaction(transfer);
        assertTrue(deleted);
        assertNull(transactionDAO.getById(transfer.getId()));

        //verify fromAccount balance updated
        BasicAccount updatedFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //verify toAccount balance updated
        BasicAccount updatedToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(500.00)));

        assertEquals(0, toAccount.getIncomingTransactions().size());
        assertEquals(0, testLedger.getTransactions().size());
    }

    //edit
    @Test
    public void testEditIncome_Success() throws SQLException {
        LedgerCategory category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        Transaction income=transactionController.createIncome(
                testLedger,
                testAccount,
                category,
                "June Salary",
                LocalDate.of(2024,6,30),
                BigDecimal.valueOf(5000.00)
        );
        Account newAccount = accountController.createBasicAccount(
                "New Account",
                BigDecimal.valueOf(2000.00),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                "New Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);

        LedgerCategory newCategory = newLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Bonus"))
                .findFirst()
                .orElse(null);


        boolean result=transactionController.updateTransaction(
                income,
                null,
                newAccount,
                newCategory,
                "Updated June Salary",
                LocalDate.of(2024,6,30),
                BigDecimal.valueOf(6000.00),
                newLedger);
        assertTrue(result);

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, testAccount.getIncomingTransactions().size());

        BasicAccount updatedNewAccount = (BasicAccount) accountDAO.getAccountById(newAccount.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(8000.00)));
        assertEquals(1, newAccount.getIncomingTransactions().size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertEquals("Updated June Salary", updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(LocalDate.of(2024,6,30), updatedIncome.getDate());

        assertEquals(1, newLedger.getTransactions().size());
        assertEquals(0, testLedger.getTransactions().size());
        assertEquals(1, newCategory.getTransactions().size());
        assertEquals(0, category.getTransactions().size());
    }

    @Test
    public void testEditExpense_Success() throws SQLException {
        LedgerCategory category = testLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        Transaction expense=transactionController.createExpense(
                testLedger,
                testAccount,
                category,
                "Grocery Shopping",
                LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00)
        );
        Account newAccount = accountController.createBasicAccount(
                "New Account",
                BigDecimal.valueOf(500.00),
                AccountType.CASH,
                AccountCategory.FUNDS,
                testUser,
                "New Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);

        LedgerCategory newCategory = newLedger.getCategories().stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);
        boolean result=transactionController.updateTransaction(
                expense,
                newAccount,
                null,
                newCategory,
                "Updated Grocery Shopping",
                LocalDate.of(2024,6,26),
                BigDecimal.valueOf(200.00),
                newLedger);
        assertTrue(result);

        //verify account balance updated
        BasicAccount updatedAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, testAccount.getOutgoingTransactions().size());

        BasicAccount updatedNewAccount = (BasicAccount) accountDAO.getAccountById(newAccount.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(300.00)));
        assertEquals(1, newAccount.getOutgoingTransactions().size());

        Transaction updatedExpense = transactionDAO.getById(expense.getId());
        assertEquals("Updated Grocery Shopping", updatedExpense.getNote());
        assertEquals(0, updatedExpense.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024,6,26), updatedExpense.getDate());

        assertEquals(1, newLedger.getTransactions().size());
        assertEquals(0, testLedger.getTransactions().size());
        assertEquals(1, newCategory.getTransactions().size());
        assertEquals(0, category.getTransactions().size());
    }

    @Test
    public void testEditTransfer_Success() throws SQLException {
        BasicAccount toAccount = accountController.createBasicAccount("Savings Account",
                BigDecimal.valueOf(500.00), AccountType.DEBIT_CARD, AccountCategory.FUNDS,
                testUser, "Savings Account Notes", true, true);

        Transaction transfer = transactionController.createTransfer(testLedger, testAccount,
                toAccount, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        Account newFromAccount = accountController.createBasicAccount("New From Account",
                BigDecimal.valueOf(300.00), AccountType.CASH, AccountCategory.FUNDS, testUser,
                "New From Account Notes", true, true);

        Account newToAccount = accountController.createBasicAccount("New To Account", BigDecimal.valueOf(400.00),
                AccountType.CASH, AccountCategory.FUNDS, testUser, "New To Account Notes",
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger", testUser);

        boolean result = transactionController.updateTransaction(transfer, newFromAccount, newToAccount,
                null, "Updated Transfer", LocalDate.of(2024, 6, 21),
                BigDecimal.valueOf(250.00), newLedger);
        assertTrue(result);

        //verify old fromAccount balance updated
        BasicAccount updatedOldFromAccount = (BasicAccount) accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedOldFromAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, testAccount.getOutgoingTransactions().size());

        //verify old toAccount balance updated
        BasicAccount updatedOldToAccount = (BasicAccount) accountDAO.getAccountById(toAccount.getId());
        assertEquals(0, updatedOldToAccount.getBalance().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, toAccount.getIncomingTransactions().size());

        //verify new fromAccount balance updated
        BasicAccount updatedNewFromAccount = (BasicAccount) accountDAO.getAccountById(newFromAccount.getId());
        assertEquals(0, updatedNewFromAccount.getBalance().compareTo(BigDecimal.valueOf(50.00)));
        assertEquals(1, newFromAccount.getOutgoingTransactions().size());

        //verify new toAccount balance updated
        BasicAccount updatedNewToAccount = (BasicAccount) accountDAO.getAccountById(newToAccount.getId());
        assertEquals(0, updatedNewToAccount.getBalance().compareTo(BigDecimal.valueOf(650.00)));
        assertEquals(1, newToAccount.getIncomingTransactions().size());

        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Updated Transfer", updatedTransfer.getNote());
        assertEquals(0, updatedTransfer.getAmount().compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(LocalDate.of(2024, 6, 21), updatedTransfer.getDate());

        assertEquals(1, newLedger.getTransactions().size());
        assertEquals(0, testLedger.getTransactions().size());
    }

}


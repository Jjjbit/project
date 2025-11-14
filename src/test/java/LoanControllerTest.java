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
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoanControllerTest {
    private Connection connection;
    private User testUser;
    private Ledger testLedger;
    private Account testAccount;

    private AccountController accountController;
    private LoanController loanController;


    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;


    @BeforeEach
    public void setUp() throws SQLException {
        connection= ConnectionManager.getConnection();
        readResetScript();
        runSchemaScript();

        UserDAO userDAO = new UserDAO(connection);
        accountDAO = new AccountDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        accountController = new AccountController(accountDAO, transactionDAO);
        loanController = new LoanController(accountDAO, transactionDAO);
        LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);


        userController.register("test user", "password123"); // create test user and insert into db
        testUser=userController.login("test user", "password123"); // login to set current user

        testLedger=ledgerController.createLedger("Test Ledger", testUser);

        testAccount = accountController.createBasicAccount("Cash Wallet", BigDecimal.valueOf(500),
                AccountType.CASH, AccountCategory.FUNDS, testUser, null, true, true);
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
    public void testCreateBorrowing_NoToAccount() throws SQLException {
        BorrowingAccount account = loanController.createBorrowingAccount(testUser, "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);
        assertNotNull(account);

        Account savedAccount = accountDAO.getAccountById(account.getId());
        assertNotNull(savedAccount);
        assertEquals(AccountType.BORROWING, savedAccount.getType());
        assertEquals(AccountCategory.VIRTUAL_ACCOUNT, savedAccount.getCategory());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(savedAccount.getSelectable());
        assertTrue(savedAccount.getIncludedInNetAsset());
        assertEquals(0, ((BorrowingAccount)savedAccount).getBorrowingAmount().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(0, ((BorrowingAccount)savedAccount).getRemainingAmount().compareTo(BigDecimal.valueOf(3000.00)));

        List<Account> userAccounts = accountDAO.getAccountsByOwnerId(testUser.getId());
        assertEquals(2, userAccounts.size());

        List<Transaction> transactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(1, transactions.size()); //initial borrowing transaction
        Transaction tx=transactions.getFirst();
        assertEquals(0, tx.getAmount().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(TransactionType.TRANSFER, tx.getType());
        assertEquals(savedAccount.getId(), tx.getFromAccount().getId());
        assertNull(tx.getToAccount());

        assertEquals(2, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(-2500.00)));
    }

    @Test
    public void testCreateBorrowing_WithToAccount() throws SQLException {
        BorrowingAccount borrowingAccount = loanController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null, true, true, testAccount, LocalDate.now(), testLedger);

        Account savedBorrowingAccount = accountDAO.getAccountById(borrowingAccount.getId());
        assertNotNull(savedBorrowingAccount);

        Account savedToAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, savedToAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00))); //500 + 1500

        List<Transaction> borrowingTransactions = transactionDAO.getByAccountId(borrowingAccount.getId());
        assertEquals(1, borrowingTransactions.size()); //initial borrowing transaction
        Transaction txBorrowing=borrowingTransactions.getFirst();
        assertEquals(0, txBorrowing.getAmount().compareTo(BigDecimal.valueOf(1500.00)));

        List<Transaction> toAccountTransactions = transactionDAO.getByAccountId(testAccount.getId());
        assertEquals(1, toAccountTransactions.size()); //initial borrowing transaction in toAccount
        Transaction txToAccount=toAccountTransactions.getFirst();
        assertEquals(txBorrowing.getId(), txToAccount.getId()); //same transaction
        assertEquals(savedToAccount.getId(), txToAccount.getToAccount().getId());

        assertEquals(2, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(2000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1500.00)));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, testAccount.getBalance().compareTo(BigDecimal.valueOf(2000.00)));
    }

    @Test
    public void testDeleteBorrowing_DeleteTransaction() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);

        Transaction tx=account.getTransactions().getFirst(); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(500.00)));
    }

    @Test
    public void testDeleteBorrowing_Success_KeepTransaction() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Eve",
                BigDecimal.valueOf(2000.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);

        Transaction tx=account.getTransactions().getFirst(); //initial borrowing transaction

        boolean result = accountController.deleteAccount(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(500.00)));
    }

    @Test
    public void testDeleteBorrowing_ToAccount_DeleteTransaction() throws SQLException {
        BorrowingAccount account = loanController.createBorrowingAccount(testUser, "Charlie",
                BigDecimal.valueOf(2500.00), //amount borrowed
                null, true, true, testAccount, LocalDate.now(), testLedger);

        Transaction tx=account.getTransactions().getFirst(); //initial borrowing transaction

        boolean result = loanController.delete(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(3000.00)));

        assertNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());

        List<Transaction> toAccountTransactions = transactionDAO.getByAccountId(testAccount.getId());
        assertEquals(0, toAccountTransactions.size());

        Account savedToAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, savedToAccount.getBalance().compareTo(BigDecimal.valueOf(3000.00))); //500+2500
    }

    @Test
    public void testDeleteBorrowing_ToAccount_KeepTransaction() throws SQLException {
        BorrowingAccount account = loanController.createBorrowingAccount(testUser, "Charlie",
                BigDecimal.valueOf(2500.00), //amount borrowed
                null, true, true, testAccount, LocalDate.now(), testLedger);

        Transaction tx=account.getTransactions().getFirst(); //initial borrowing transaction
        List<Transaction> toAccountTransactionsBefore = transactionDAO.getByAccountId(testAccount.getId());
        assertEquals(1, toAccountTransactionsBefore.size());

        boolean result = loanController.delete(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(3000.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(3000.00)));

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());

        List<Transaction> toAccountTransactions = transactionDAO.getByAccountId(testAccount.getId());
        assertEquals(1, toAccountTransactions.size());
    }

    @Test
    public void testDeleteLending_Success_DeleteTransaction() throws SQLException {
        LendingAccount account = loanController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(100.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        Transaction tx=account.getTransactions().getFirst(); //initial lending transaction

        boolean result = loanController.delete(account, true);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(500.00)));

        assertNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(0, ledgerTransactions.size());
    }

    @Test
    public void testDeleteLending_Success_KeepTransaction() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        Transaction tx=account.getTransactions().getFirst(); //initial lending transaction

        List<Transaction> ledgerTransactionsBefore = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactionsBefore.size());

        boolean result = loanController.delete(account, false);
        assertTrue(result);

        Account deletedAccount = accountDAO.getAccountById(account.getId());
        assertNull(deletedAccount);

        assertNotNull(transactionDAO.getById(tx.getId()));
        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(1, ledgerTransactions.size());

        assertEquals(1, testUser.getAccounts().size());
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(500.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.ZERO));
        assertEquals(0, testUser.getNetAssets().compareTo(BigDecimal.valueOf(500.00)));
    }

    @Test
    public void testPayBorrowing_NullFromAccount() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger);

        boolean result=loanController.payBorrowing(account, BigDecimal.valueOf(300.00), null, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());

        BorrowingAccount savedAccount= (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(1200.00)));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(1200.00)));
        assertEquals(2, account.getTransactions().size()); //initial borrowing + repayment
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1200.00)));
    }

    @Test
    public void testPayBorrowing_WithFromAccount() throws SQLException {

        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Alice",
                BigDecimal.valueOf(1500.00), //amount borrowed
                null, true, true, null, LocalDate.now(), testLedger
        );

        boolean result=loanController.payBorrowing(account, BigDecimal.valueOf(400.00), testAccount, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());
        List<Transaction> fromAccountTransactions = transactionDAO.getByAccountId(testAccount.getId());
        assertEquals(1, fromAccountTransactions.size());

        BorrowingAccount savedAccount= (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(1100.00)));

        Account updatedFromAccount= accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(100.00)));

        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(1100.00)));
        assertEquals(2, account.getTransactions().size());
        assertEquals(1, testAccount.getTransactions().size());
        assertEquals(0, testAccount.getBalance().compareTo(BigDecimal.valueOf(100.00)));
        assertEquals(0, testUser.getTotalLiabilities().compareTo(BigDecimal.valueOf(1100.00)));
    }

    @Test
    public void testReceiveLending_NullToAccount() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        boolean result=loanController.receiveLending(account, BigDecimal.valueOf(200.00), null, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());

        LendingAccount savedAccount= (LendingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(800.00)));
        assertEquals(2, account.getTransactions().size()); //initial lending + receiving
        assertEquals(0, testUser.getTotalAssets().compareTo(BigDecimal.valueOf(1300.00)));
    }

    @Test
    public void testReceiveLending_WithToAccount() throws SQLException {

        LendingAccount account = accountController.createLendingAccount(testUser, "Frank",
                BigDecimal.valueOf(1000.00), //amount lent
                null, true, true, null, LocalDate.now(), testLedger);

        boolean result=loanController.receiveLending(account, BigDecimal.valueOf(300.00), testAccount, testLedger);
        assertTrue(result);

        List<Transaction> ledgerTransactions = transactionDAO.getByLedgerId(testLedger.getId());
        assertEquals(2, ledgerTransactions.size());
        List<Transaction> accountTransactions = transactionDAO.getByAccountId(account.getId());
        assertEquals(2, accountTransactions.size());
        List<Transaction> toAccountTransactions = transactionDAO.getByAccountId(testAccount.getId());
        assertEquals(1, toAccountTransactions.size());

        LendingAccount savedAccount= (LendingAccount) accountDAO.getAccountById(account.getId());
        assertEquals(0, savedAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));

        Account updatedToAccount= accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(700.00)));
        assertEquals(2, account.getTransactions().size());
        assertEquals(1, testAccount.getTransactions().size()); //transfer to toAccount transaction
        assertEquals(0, testAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));
    }

    @Test
    public void testEditBorrowingAccount_Success() throws SQLException {
        BorrowingAccount account = accountController.createBorrowingAccount(testUser, "Bob",
                BigDecimal.valueOf(3000.00), //amount borrowed
                "Initial Notes", true, true, null, LocalDate.now(), testLedger
        );

        boolean result = loanController.editBorrowingAccount(account, "Updated Bob",
                BigDecimal.valueOf(2500.00), //new amount borrowed
                "Updated Notes", false, false, true);
        assertTrue(result);
        assertEquals(0, account.getRemainingAmount().compareTo(BigDecimal.valueOf(2500.00)));

        BorrowingAccount editedAccount = (BorrowingAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Bob", editedAccount.getName());
        assertEquals(0, editedAccount.getRemainingAmount().compareTo(BigDecimal.valueOf(2500.00)));
        assertEquals(0, editedAccount.getBorrowingAmount().compareTo(BigDecimal.valueOf(2500.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertTrue(editedAccount.getIsEnded());
    }

    @Test
    public void testEditLendingAccount_Success() throws SQLException {
        LendingAccount account = accountController.createLendingAccount(testUser, "Charlie",
                BigDecimal.valueOf(4000.00), //amount lent
                "Initial Notes", true, true, null, LocalDate.now(), testLedger);

        boolean result = loanController.editLendingAccount(account, "Updated Charlie",
                BigDecimal.valueOf(3500.00), //new amount lent
                "Updated Notes", false, false, true);
        assertTrue(result);
        assertEquals(0, account.getBalance().compareTo(BigDecimal.valueOf(3500.00)));

        LendingAccount editedAccount = (LendingAccount) accountDAO.getAccountById(account.getId());
        assertNotNull(editedAccount);
        assertEquals("Updated Charlie", editedAccount.getName());
        assertEquals(0, editedAccount.getBalance().compareTo(BigDecimal.valueOf(3500.00)));
        assertEquals("Updated Notes", editedAccount.getNotes());
        assertFalse(editedAccount.getIncludedInNetAsset());
        assertFalse(editedAccount.getSelectable());
        assertTrue(editedAccount.getIsEnded());
    }
}

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

public class TransactionControllerTest {
    private Connection connection;

    private Ledger testLedger;
    private Account testAccount;
    private Account testAccount1;
    private Account testAccount2;
    private LedgerCategory salary;
    private LedgerCategory shopping;
    private LedgerCategory food;

    private AccountDAO accountDAO;
    private TransactionDAO transactionDAO;
    private LedgerCategoryDAO ledgerCategoryDAO;

    private TransactionController transactionController;
    private LedgerController ledgerController;
    private AccountController accountController;

    @BeforeEach
    public void setUp() {
        ConnectionManager connectionManager= ConnectionManager.getInstance();
        connection=connectionManager.getConnection();
        readResetScript();
        runSchemaScript();
        readDataScript();

        UserDAO userDAO = new UserDAO(connection);
        LedgerDAO ledgerDAO = new LedgerDAO(connection);
        ledgerCategoryDAO = new LedgerCategoryDAO(connection);
        accountDAO = new AccountDAO(connection);
        transactionDAO = new TransactionDAO(connection);
        CategoryDAO categoryDAO = new CategoryDAO(connection);
        BudgetDAO budgetDAO = new BudgetDAO(connection);

        UserController userController = new UserController(userDAO);
        transactionController = new TransactionController(transactionDAO, accountDAO);
        ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
        accountController = new AccountController(accountDAO, transactionDAO);

        userController.register("test user", "password123");
        userController.login("test user", "password123");

        testLedger = ledgerController.createLedger("Test Ledger");

        List<LedgerCategory> testCategories = ledgerCategoryDAO.getTreeByLedger(testLedger);
        salary = testCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        shopping = testCategories.stream()
                .filter(cat -> cat.getName().equals("Shopping"))
                .findFirst()
                .orElse(null);
        food = testCategories.stream()
                .filter(cat -> cat.getName().equals("Food"))
                .findFirst()
                .orElse(null);

        testAccount = accountController.createAccount("Test Account", BigDecimal.valueOf(1000.00), true, true);
        testAccount1 = accountController.createAccount("Test Account 1", BigDecimal.valueOf(500.00), true, true);
        testAccount2 = accountController.createAccount("Test Account 2", BigDecimal.valueOf(300.00), true, true);
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
    public void testCreateIncome_Success() {
        Transaction income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));
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
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00))); //1000 + 5000 = 6000
    }

    @Test
    public void testCreateIncome_Failure() {
        assertNull(transactionController.createIncome(null, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00))); //null ledger
        assertNull(transactionController.createIncome(testLedger, null, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00))); //null account
        assertNull(transactionController.createIncome(testLedger, testAccount, null, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00))); //null category
        assertNull(transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024, 6, 30), BigDecimal.valueOf(-100.00))); //negative amount

        testAccount.setSelectable(false);
        assertNull(transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00))); //non-selectable account
    }

    @Test
    public void testCreateExpense_Success() {
        Transaction expense=transactionController.createExpense(testLedger, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00));
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
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00))); //1000 - 150 = 850
    }

    @Test
    public void testCreateExpense_Failure(){
        assertNull(transactionController.createExpense(null, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00))); //null ledger
        assertNull(transactionController.createExpense(testLedger, null, shopping, "Grocery Shopping", LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00))); //null account
        assertNull(transactionController.createExpense(testLedger, testAccount, null, "Grocery Shopping", LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00))); //null category
        assertNull(transactionController.createExpense(testLedger, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25), BigDecimal.valueOf(-50.00))); //negative amount

        testAccount.setSelectable(false);
        assertNull(transactionController.createExpense(testLedger, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25), BigDecimal.valueOf(150.00))); //non-selectable account
    }

    @Test
    public void testCreateTransfer_Success() {
        Transaction transfer=transactionController.createTransfer(testLedger, testAccount, testAccount1,
                "Transfer to Savings", LocalDate.of(2024,6,20),
                BigDecimal.valueOf(200.00));
        assertNotNull(transfer);
        assertNotNull(transactionDAO.getById(transfer.getId()));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());

        Transaction fetchedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Transfer to Savings", fetchedTransfer.getNote());
        assertEquals(0, fetchedTransfer.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024,6,20), fetchedTransfer.getDate());
        assertEquals(testAccount.getId(), fetchedTransfer.getFromAccount().getId());
        assertEquals(testAccount1.getId(), fetchedTransfer.getToAccount().getId());
        assertNull(fetchedTransfer.getCategory());
        assertEquals(TransactionType.TRANSFER, fetchedTransfer.getType());

        //verify fromAccount balance updated
        Account updatedFromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //1000 - 200 = 800

        //verify toAccount balance updated
        Account updatedToAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500 + 200 = 700
    }

    @Test
    public void testCreateTransfer_Failure(){
        assertNull(transactionController.createTransfer(null, testAccount, testAccount1, "Transfer to Savings", LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00))); //null ledger
        assertNull(transactionController.createTransfer(testLedger, testAccount, testAccount1, "Transfer to Savings", LocalDate.of(2024,6,20), BigDecimal.valueOf(-50.00))); //negative amount
        assertNull(transactionController.createTransfer(testLedger, null, null, "Transfer to Savings", LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00))); //null fromAccount
        assertNull(transactionController.createTransfer(testLedger, testAccount, testAccount, "Transfer to Savings", LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00))); //null toAccount

        testAccount.setSelectable(false);
        assertNull(transactionController.createTransfer(testLedger, testAccount, testAccount1, "Transfer to Savings", LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00))); //non-selectable fromAccount

        testAccount1.setSelectable(false);
        assertNull(transactionController.createTransfer(testLedger, testAccount2, testAccount1, "Transfer to Savings", LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00))); //non-selectable toAccount
    }

    //delete
    @Test
    public void testDeleteIncome_Success() {
        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));
        assertNotNull(income);

        assertTrue(transactionController.deleteTransaction(income));
        assertNull(transactionDAO.getById(income.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify account balance updated
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteExpense_Success() {
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
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testDeleteTransfer_Success() {
        Transaction transfer=transactionController.createTransfer(testLedger, testAccount, testAccount1,"Transfer to Savings",
                LocalDate.of(2024,6,20), BigDecimal.valueOf(200.00));
        assertNotNull(transfer);
        assertTrue(transactionController.deleteTransaction(transfer));
        assertNull(transactionDAO.getById(transfer.getId()));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(0, transactionDAO.getByAccountId(testAccount1.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());

        //verify fromAccount balance updated
        Account updatedFromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        //verify toAccount balance updated
        Account updatedToAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(500.00)));
    }

    //edit
    @Test
    public void testEditIncome_Success() {
        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        Ledger newLedger = ledgerController.createLedger("New Ledger");
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedger(newLedger);

        LedgerCategory newSalary = newLedgerCategories.stream()
                .filter(cat -> cat.getName().equals("Salary"))
                .findFirst()
                .orElse(null);
        assertNotNull(newSalary);

        boolean result=transactionController.updateIncome(income, testAccount1, newSalary, null, LocalDate.of(2025,6,30), BigDecimal.valueOf(6000.00), newLedger);
        assertTrue(result);

        //verify account balance updated
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());

        Account updatedNewAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedNewAccount.getBalance().compareTo(BigDecimal.valueOf(6500.00))); //500 + 6000 = 6500
        assertEquals(1, transactionDAO.getByAccountId(testAccount1.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(newLedger.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(newSalary.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(salary.getId()).size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertNull(updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(LocalDate.of(2025,6,30), updatedIncome.getDate());
        assertEquals(testAccount1.getId(), updatedIncome.getToAccount().getId());
        assertNull(updatedIncome.getFromAccount());
        assertEquals(newSalary.getId(), updatedIncome.getCategory().getId());
        assertEquals(newLedger.getId(), updatedIncome.getLedger().getId());
    }

    @Test
    public void testEditIncome_Invariant() {
        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));

        boolean result=transactionController.updateIncome(income, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), testLedger);
        assertTrue(result);

        //verify account balance unchanged
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(6000.00)));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(salary.getId()).size());

        Transaction updatedIncome = transactionDAO.getById(income.getId());
        assertEquals("June Salary", updatedIncome.getNote());
        assertEquals(0, updatedIncome.getAmount().compareTo(BigDecimal.valueOf(5000.00)));
        assertEquals(LocalDate.of(2024,6,30), updatedIncome.getDate());
        assertEquals(testAccount.getId(), updatedIncome.getToAccount().getId());
    }

    @Test
    public void testEditIncome_Failure(){
        Income income=transactionController.createIncome(testLedger, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00));
        //null income
        assertFalse(transactionController.updateIncome(null, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), testLedger));
        //null account
        assertFalse(transactionController.updateIncome(income, null, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), testLedger));
        //null ledger
        assertFalse(transactionController.updateIncome(income, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), null));
        //null amount
        assertFalse(transactionController.updateIncome(income, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), null, testLedger));
        //null category
        assertFalse(transactionController.updateIncome(income, testAccount, null, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), testLedger));
        //different category type
        assertFalse(transactionController.updateIncome(income, testAccount, shopping, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), testLedger));
        //negative amount
        assertFalse(transactionController.updateIncome(income, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(-100.00), testLedger));
        //non-selectable account
        testAccount.setSelectable(false);
        assertFalse(transactionController.updateIncome(income, testAccount, salary, "June Salary", LocalDate.of(2024,6,30), BigDecimal.valueOf(5000.00), testLedger));
    }

    @Test
    public void testEditExpense_Success() {
        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        Account newAccount = accountController.createAccount("New Account", BigDecimal.valueOf(500.00),
                true, true);

        Ledger newLedger = ledgerController.createLedger("New Ledger");
        List<LedgerCategory> newLedgerCategories = ledgerCategoryDAO.getTreeByLedger(newLedger);

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
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));

        assertEquals(0, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByAccountId(newAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(newLedger.getId()).size());
        assertEquals(0, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(newCategory.getId()).size());
        assertEquals(0, transactionDAO.getByCategoryId(shopping.getId()).size());

        Account updatedNewAccount = accountDAO.getAccountById(newAccount.getId());
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
    public void testEditExpense_Invariant() {
        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping,
                "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));

        boolean result=transactionController.updateExpense(expense, testAccount,
                shopping, "Grocery Shopping", LocalDate.of(2024, 6, 25), BigDecimal.valueOf(150.00), testLedger);
        assertTrue(result);

        //verify account balance unchanged
        Account updatedAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedAccount.getBalance().compareTo(BigDecimal.valueOf(850.00)));
        assertEquals(1, transactionDAO.getByAccountId(testAccount.getId()).size());
        assertEquals(1, transactionDAO.getByLedgerId(testLedger.getId()).size());
        assertEquals(1, transactionDAO.getByCategoryId(shopping.getId()).size());

        Transaction updatedExpense = transactionDAO.getById(expense.getId());
        assertEquals("Grocery Shopping", updatedExpense.getNote());
        assertEquals(0, updatedExpense.getAmount().compareTo(BigDecimal.valueOf(150.00)));
        assertEquals(LocalDate.of(2024,6,25), updatedExpense.getDate());
        assertEquals(testAccount.getId(), updatedExpense.getFromAccount().getId());
    }

    @Test
    public void testEditExpense_Failure(){
        Expense expense=transactionController.createExpense(testLedger, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00));
        //null expense
        assertFalse(transactionController.updateExpense(null, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00), testLedger));

        //null account
        assertFalse(transactionController.updateExpense(expense, null, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00), testLedger));
        //null ledger
        assertFalse(transactionController.updateExpense(expense, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00), null));
        //null amount
        assertFalse(transactionController.updateExpense(expense, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                null, testLedger));
        //null category
        assertFalse(transactionController.updateExpense(expense, testAccount, null, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00), testLedger));
        //different category type
        assertFalse(transactionController.updateExpense(expense, testAccount, salary, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00), testLedger));

        //negative amount
        assertFalse(transactionController.updateExpense(expense, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(-50.00), testLedger));

        //non-selectable account
        testAccount.setSelectable(false);
        assertFalse(transactionController.updateExpense(expense, testAccount, shopping, "Grocery Shopping", LocalDate.of(2024,6,25),
                BigDecimal.valueOf(150.00), testLedger));
    }

//    @Test
//    public void testUpdateAmountOnly(){
//        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00));
//
//        boolean result = transactionController.updateTransfer(transfer, testAccount,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(300.00), testLedger);
//        assertTrue(result);
//
//        //verify account balances updated
//        Account updatedFromAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //1000-200+200-300=700
//
//        Account updatedToAccount = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //500+200-200+300=800
//
//    }
//
//    @Test
//    public void testChangeFromAccountOnly(){
//        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00));
//
//        boolean result = transactionController.updateTransfer(transfer, testAccount2,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        Account updateTestAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //1000-200+200=1000
//        Account updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
//        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(100.00))); //300-200=100
//        Account updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500+200=700
//
//        //change from account to null
//        result = transactionController.updateTransfer(transfer, null,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
//        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //100+200=300
//        updateTestAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //1000 unchanged
//        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(700.00))); //700 unchanged
//
//
//        //change from account from null to testAccount
//        result = transactionController.updateTransfer(transfer, testAccount,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
//        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //
//        updateTestAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //1000-200=800
//        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(700.00))); //700 unchanged
//    }
//
//    //change toAccount only
//    @Test
//    public void testChangeToAccountOnly(){
//        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00));
//        //change toAccount
//        boolean result = transactionController.updateTransfer(transfer, testAccount,
//                testAccount2, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        Account updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //1000-200=800
//        Account updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(500.00))); //500+200-200=500
//        Account updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
//        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(500.00))); //300+200=500
//
//        //change toAccount to null
//        result = transactionController.updateTransfer(transfer, testAccount,
//                null, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //800 unchanged
//        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
//        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //500-200=300
//        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(500.00))); //500 unchanged
//
//        //change toAccount from null to testAccount1
//        result = transactionController.updateTransfer(transfer, testAccount,
//                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(800.00))); //800 unchanged
//        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
//        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //300 unchanged
//        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
//        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(700.00))); //500+200=700
//    }

    //change fromAccount and amount
    @Test
    public void testChangeFromAccountAndAmount(){
        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        boolean result = transactionController.updateTransfer(transfer, testAccount2,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(300.00), testLedger);
        assertTrue(result);
        //verify account balances updated
        Account updateTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //1000-200+200=1000
        Account updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.ZERO)); //300-300=0
        Account updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(800.00))); //500+300=800

        //change fromAccount to null
        result = transactionController.updateTransfer(transfer, null,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(400.00), testLedger);
        assertTrue(result);
        //verify account balances updated
        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //0+300=300
        updateTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //1000 unchanged
        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(900.00))); //

        //change fromAccount from null to testAccount
        result = transactionController.updateTransfer(transfer, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(500.00), testLedger);
        assertTrue(result);
        //verify account balances updated
        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00)));
        updateTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updateTestAccount.getBalance().compareTo(BigDecimal.valueOf(500.00))); //1000-500=500
        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //900 unchanged
    }

    //change toAccount and amount
    @Test
    public void testChangeToAccountAndAmount(){
        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));
        boolean result = transactionController.updateTransfer(transfer, testAccount,
                testAccount2, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(300.00), testLedger);
        assertTrue(result);
        //verify account balances updated
        Account updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(700.00))); //1000-300=700
        Account updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(500.00))); //500+200-200=500
        Account updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(600.00))); //300+300=600

        //change toAccount to null
        result = transactionController.updateTransfer(transfer, testAccount,
                null, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(400.00), testLedger);
        assertTrue(result);
        //verify account balances updated
        updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(600.00))); //700-400=600
        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //600-300=300
        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(500.00))); //500 unchanged

        //change toAccount from null to testAccount1
        result = transactionController.updateTransfer(transfer, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(500.00), testLedger);
        assertTrue(result);
        //verify account balances updated
        updatedTestAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedTestAccount.getBalance().compareTo(BigDecimal.valueOf(500.00))); //600-500=500
        updatedTestAccount2 = accountDAO.getAccountById(testAccount2.getId());
        assertEquals(0, updatedTestAccount2.getBalance().compareTo(BigDecimal.valueOf(300.00))); //600 unchanged
        updatedTestAccount1 = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedTestAccount1.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
    }

    @Test
    public void testEditTransfer_Failure(){
        assertFalse(transactionController.updateTransfer(null, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00), testLedger));

        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));
        //same from and to account
        assertFalse( transactionController.updateTransfer(transfer, testAccount1,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00), testLedger));

        //negative amount
        assertFalse(transactionController.updateTransfer(transfer, null,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(-200.00), testLedger));

        assertFalse(transactionController.updateTransfer(transfer, null,
                null, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00), testLedger)); //both from and to account null

        testAccount2.setSelectable(false);
        //from account not selectable
        assertFalse(transactionController.updateTransfer(transfer, testAccount2,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00), testLedger));


        //to account not selectable
        assertFalse(transactionController.updateTransfer(transfer, testAccount,
                testAccount2, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00), testLedger));
    }

    @Test
    public void testEditTransfer_Success() { //change all fields
        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        Ledger newLedger = ledgerController.createLedger("New Ledger");

        boolean result = transactionController.updateTransfer(transfer, testAccount1, testAccount,
                "Updated Transfer", LocalDate.of(2024, 6, 21),
                BigDecimal.valueOf(250.00), newLedger);
        assertTrue(result);

        //verify account balances updated
        Account updatedFromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(1250.00))); //1000-200+200+250=1250

        Account updatedToAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(250.00))); //500+200-200-250=250

        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Updated Transfer", updatedTransfer.getNote());
        assertEquals(0, updatedTransfer.getAmount().compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(LocalDate.of(2024, 6, 21), updatedTransfer.getDate());
        assertEquals(testAccount1.getId(), updatedTransfer.getFromAccount().getId());
        assertEquals(testAccount.getId(), updatedTransfer.getToAccount().getId());
        assertEquals(newLedger.getId(), updatedTransfer.getLedger().getId());
    }

    @Test
    public void testEditTransfer_Invariant() { //no changes
        Transfer transfer = transactionController.createTransfer(testLedger, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00));

        boolean result = transactionController.updateTransfer(transfer, testAccount,
                testAccount1, "Transfer to Savings", LocalDate.of(2024, 6, 20),
                BigDecimal.valueOf(200.00), testLedger);
        assertTrue(result);

        //verify account balances unchanged
        Account updatedFromAccount = accountDAO.getAccountById(testAccount.getId());
        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(800.00)));

        Account updatedToAccount = accountDAO.getAccountById(testAccount1.getId());
        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(700.00)));

        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
        assertEquals("Transfer to Savings", updatedTransfer.getNote());
        assertEquals(0, updatedTransfer.getAmount().compareTo(BigDecimal.valueOf(200.00)));
        assertEquals(LocalDate.of(2024, 6, 20), updatedTransfer.getDate());
        assertEquals(testAccount.getId(), updatedTransfer.getFromAccount().getId());
        assertEquals(testAccount1.getId(), updatedTransfer.getToAccount().getId());
        assertEquals(testLedger.getId(), updatedTransfer.getLedger().getId());
    }

    //test edit transfer: old fromAccount is null and new fromAccount is null &change toAccount
//    @Test
//    public void testUpdateTransfer1(){
//        Transfer transfer = transactionController.createTransfer(testLedger, null,
//                testAccount, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00));
//
//        Account newToAccount = accountController.createAccount("New Savings Account",
//                BigDecimal.valueOf(300.00), true, true);
//
//        boolean result = transactionController.updateTransfer(transfer, null, newToAccount, "Transfer to Savings",
//                LocalDate.of(2024, 6, 20), BigDecimal.valueOf(20.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        Account updatedToAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00)));
//
//        Account updatedNewToAccount = accountDAO.getAccountById(newToAccount.getId());
//        assertEquals(0, updatedNewToAccount.getBalance().compareTo(BigDecimal.valueOf(320.00))); //300+20
//
//        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
//        assertNull(updatedTransfer.getFromAccount());
//        assertEquals(newToAccount.getId(), updatedTransfer.getToAccount().getId());
//    }
    //test edit transfer: old fromAccount is null and new fromAccount is not null & old toAccount is not null and new toAccount is null
//    @Test
//    public void testUpdateTransfer2(){
//        Account fromAccount = accountController.createAccount("Savings Account",
//                BigDecimal.valueOf(500.00), true, true);
//        Transfer transfer = transactionController.createTransfer(testLedger, null,
//                testAccount, "Transfer to Savings", LocalDate.of(2024, 6, 20),
//                BigDecimal.valueOf(200.00));
//
//        boolean result = transactionController.updateTransfer(transfer, fromAccount, null, "Transfer to Savings",
//                LocalDate.of(2024, 6, 20), BigDecimal.valueOf(20.00), testLedger);
//        assertTrue(result);
//        //verify account balances updated
//        Account updatedFromAccount = accountDAO.getAccountById(fromAccount.getId());
//        assertEquals(0, updatedFromAccount.getBalance().compareTo(BigDecimal.valueOf(480))); //500 - 20 = 480
//
//        Account updatedToAccount = accountDAO.getAccountById(testAccount.getId());
//        assertEquals(0, updatedToAccount.getBalance().compareTo(BigDecimal.valueOf(1000.00))); //1000-200+200=1000
//
//        Transaction updatedTransfer = transactionDAO.getById(transfer.getId());
//        assertEquals(fromAccount.getId(), updatedTransfer.getFromAccount().getId());
//        assertNull(updatedTransfer.getToAccount());
//
//    }

    @Test
    public void testGetTransactionsByLedgerInRangeDate_Boundary() {
        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        transactionController.createExpense(testLedger, testAccount, food, "Dinner", startDate, BigDecimal.valueOf(30.00));
        transactionController.createExpense(testLedger, testAccount, food, "Lunch", endDate, BigDecimal.valueOf(20.00));

        List<Transaction> transactions = transactionController.getTransactionsByLedgerInRangeDate(testLedger, startDate, endDate);
        assertEquals(2, transactions.size());

        for (Transaction tx : transactions) {
            StringBuilder info = new StringBuilder();

            info.append(String.format("ID: %d, Type: %s, Amount: %s, Date: %s",
                    tx.getId(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getDate()));

            if (tx.getCategory() != null) {
                info.append(", Category: ").append(tx.getCategory().getName());
            }

            if (tx instanceof Transfer) {
                if (tx.getFromAccount() != null) {
                    info.append(", FROM: ").append(tx.getFromAccount().getName());
                }
                if (tx.getToAccount() != null) {
                    info.append(", TO: ").append(tx.getToAccount().getName());
                }
            } else {
                if (tx.getFromAccount() != null) {
                    info.append(", From: ").append(tx.getFromAccount().getName());
                } else if (tx.getToAccount() != null) {
                    info.append(", To: ").append(tx.getToAccount().getName());
                }
            }

            if (tx.getNote() != null && !tx.getNote().isEmpty()) {
                info.append(", Note: ").append(tx.getNote());
            }

            System.out.println(info);
        }
    }

    @Test
    public void testGetTransactionsByAccountInRangeDate_Boundary() {
        LocalDate startDate = LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        transactionController.createExpense(testLedger, testAccount, food, "Dinner", startDate, BigDecimal.valueOf(30.00));
        transactionController.createExpense(testLedger, testAccount, food, "Lunch", endDate, BigDecimal.valueOf(20.00));
        transactionController.createTransfer(testLedger, testAccount, null, "Transfer to self", startDate, BigDecimal.valueOf(100.00));
        transactionController.createTransfer(testLedger, null, testAccount, "Transfer from self", endDate, BigDecimal.valueOf(200.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", startDate, BigDecimal.valueOf(3000.00));
        transactionController.createIncome(testLedger, testAccount, salary, "Monthly Salary", endDate, BigDecimal.valueOf(3000.00));

        List<Transaction> transactions = transactionController.getTransactionsByAccountInRangeDate(testAccount, startDate, endDate);
        assertEquals(6, transactions.size());
        for (Transaction tx : transactions) {
            StringBuilder info = new StringBuilder();

            info.append(String.format("ID: %d, Type: %s, Amount: %s, Date: %s",
                    tx.getId(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getDate()));

            if (tx.getCategory() != null) {
                info.append(", Category: ").append(tx.getCategory().getName());
            }

            if (tx instanceof Transfer) {
                if (tx.getFromAccount() != null) {
                    info.append(", FROM: ").append(tx.getFromAccount().getName());
                }
                if (tx.getToAccount() != null) {
                    info.append(", TO: ").append(tx.getToAccount().getName());
                }
            } else {
                if (tx.getFromAccount() != null) {
                    info.append(", From: ").append(tx.getFromAccount().getName());
                } else if (tx.getToAccount() != null) {
                    info.append(", To: ").append(tx.getToAccount().getName());
                }
            }

            if (tx.getNote() != null && !tx.getNote().isEmpty()) {
                info.append(", Note: ").append(tx.getNote());
            }

            System.out.println(info);
        }
    }

}


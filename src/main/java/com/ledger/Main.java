package com.ledger;

import com.ledger.business.*;
import com.ledger.cli.*;
import com.ledger.orm.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        try {
            // connect to database
            Connection connection = ConnectionManager.getConnection();
            runSchemaScript(connection);

            // create DAO layer
            UserDAO userDAO = new UserDAO(connection);
            AccountDAO accountDAO = new AccountDAO(connection);
            LedgerDAO ledgerDAO = new LedgerDAO(connection);
            TransactionDAO transactionDAO = new TransactionDAO(connection);
            InstallmentPlanDAO installmentPlanDAO = new InstallmentPlanDAO(connection);
            CategoryDAO categoryDAO = new CategoryDAO(connection);
            LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
            BudgetDAO budgetDAO = new BudgetDAO(connection);

            // create Business layer
            UserController userController = new UserController(userDAO);
            AccountController accountController = new AccountController(accountDAO, transactionDAO);
            LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
            TransactionController transactionController = new TransactionController(transactionDAO, accountDAO, ledgerDAO);
            InstallmentPlanController installmentPlanController = new InstallmentPlanController(installmentPlanDAO, transactionDAO, accountDAO);
            LedgerCategoryController ledgerCategoryController = new LedgerCategoryController(ledgerCategoryDAO, ledgerDAO, transactionDAO, budgetDAO);
            BudgetController budgetController = new BudgetController(budgetDAO, transactionDAO, ledgerCategoryDAO);
            ReportController reportController = new ReportController(transactionDAO, accountDAO, ledgerDAO, budgetDAO, installmentPlanDAO, ledgerCategoryDAO);

            //  create CLI layer
            UserCLI userCLI = new UserCLI(userController);
            AccountCLI accountCLI = new AccountCLI(accountController, userController, reportController);
            LedgerCLI ledgerCLI = new LedgerCLI(userController, reportController, ledgerController);
            TransactionCLI transactionCLI = new TransactionCLI(transactionController, reportController,userController);
            InstallmentPlanCLI installmentPlanCLI = new InstallmentPlanCLI(installmentPlanController, userController,
                    reportController);
            BudgetCLI budgetCLI = new BudgetCLI(budgetController, reportController, userController);
            BorrowingCLI borrowingCLI = new BorrowingCLI(reportController, accountController, userController);
            LendingCLI lendingCLI = new LendingCLI(reportController, accountController, userController);
            LedgerCategoryCLI ledgerCategoryCLI = new LedgerCategoryCLI(ledgerCategoryController, reportController, userController);

            // create MainCLI
            MainCLI mainCLI = new MainCLI(userCLI, accountCLI, ledgerCLI, transactionCLI,
                    installmentPlanCLI, budgetCLI,ledgerCategoryCLI, borrowingCLI, lendingCLI);

            //run application
            mainCLI.run();

            //disconnect from database
            connection.close();
        } catch (SQLException e) {
            System.err.println("Error connecting to database: " + e.getMessage());
        }
    }

    private static void runSchemaScript(Connection connection) {
        try {
            Path path = Paths.get("src/test/resources/schema.sql");
            String sql = Files.lines(path).collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            System.err.println("Failed to load schema.sql: " + e.getMessage());
        }
    }
}

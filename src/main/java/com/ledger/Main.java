package com.ledger;

import com.ledger.business.*;
import com.ledger.cli.*;
import com.ledger.orm.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        try {
            // connect to database
            Connection connection = ConnectionManager.getConnection();
            runSchemaScript(connection);
            runDataScriptIfEmpty(connection);

            // create DAO layer
            UserDAO userDAO = new UserDAO(connection);
            AccountDAO accountDAO = new AccountDAO(connection);
            LedgerDAO ledgerDAO = new LedgerDAO(connection);
            TransactionDAO transactionDAO = new TransactionDAO(connection);
            InstallmentDAO installmentDAO = new InstallmentDAO(connection);
            CategoryDAO categoryDAO = new CategoryDAO(connection);
            LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection);
            BudgetDAO budgetDAO = new BudgetDAO(connection);

            // create Business layer
            UserController userController = new UserController(userDAO);
            AccountController accountController = new AccountController(accountDAO, transactionDAO);
            LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
            TransactionController transactionController = new TransactionController(transactionDAO, accountDAO);
            InstallmentController installmentController = new InstallmentController(installmentDAO, transactionDAO, accountDAO);
            LedgerCategoryController ledgerCategoryController = new LedgerCategoryController(ledgerCategoryDAO, transactionDAO, budgetDAO);
            BudgetController budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO, transactionDAO);
            ReportController reportController = new ReportController(transactionDAO, accountDAO, ledgerDAO, budgetDAO, installmentDAO, ledgerCategoryDAO);

            //  create CLI layer
            UserCLI userCLI = new UserCLI(userController, reportController);
            AccountCLI accountCLI = new AccountCLI(accountController, userController, reportController);
            LedgerCLI ledgerCLI = new LedgerCLI(userController, reportController, ledgerController);
            TransactionCLI transactionCLI = new TransactionCLI(transactionController, reportController,userController);
            InstallmentCLI installmentCLI = new InstallmentCLI(installmentController, userController,
                    reportController);
            BudgetCLI budgetCLI = new BudgetCLI(budgetController, reportController, userController);
            BorrowingCLI borrowingCLI = new BorrowingCLI(reportController, accountController, userController);
            LendingCLI lendingCLI = new LendingCLI(reportController, accountController, userController);
            LedgerCategoryCLI ledgerCategoryCLI = new LedgerCategoryCLI(ledgerCategoryController, reportController, userController);

            // create MainCLI
            MainCLI mainCLI = new MainCLI(userCLI, accountCLI, ledgerCLI, transactionCLI,
                    installmentCLI, budgetCLI,ledgerCategoryCLI, borrowingCLI, lendingCLI);

            //run application
            mainCLI.run();

            //disconnect from database
            connection.close();
        } catch (SQLException e) {
            System.err.println("Error connecting to database: " + e.getMessage());
        }
    }

    private static void runSchemaScript(Connection connection) {
        Path path = Paths.get("src/test/resources/schema.sql");
        try (Stream<String> lines = Files.lines(path)) {
            String sql = lines.collect(Collectors.joining("\n"));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        } catch (Exception e) {
            System.err.println("Failed to load schema.sql: " + e.getMessage());
        }
    }

    @SuppressWarnings("SqlResolve")
    private static void runDataScriptIfEmpty(Connection connection) {
        try {
            // 1. check table empty
            String checkSql = "SELECT COUNT(*) FROM global_categories";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {
                rs.next();
                int count = rs.getInt(1);
                if (count > 0) return;
            }

            // 2. read file in try-with-resources
            Path path = Paths.get("src/test/resources/data.sql");
            try (Stream<String> lines = Files.lines(path)) {
                String sql = lines.collect(Collectors.joining("\n"))
                        .replaceAll(";\\s*\n", ";");

                try (Statement stmt = connection.createStatement()) {
                    for (String s : sql.split(";")) {
                        String trimmed = s.trim();
                        if (!trimmed.isEmpty()) {
                            stmt.execute(trimmed);
                        }
                    }
                }
            }

            System.out.println("global_categories initialized from data.sql");

        } catch (Exception e) {
            System.err.println("Failed to load data.sql: " + e.getMessage());
        }
    }
}

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
            LedgerCategoryDAO ledgerCategoryDAO = new LedgerCategoryDAO(connection, ledgerDAO);
            TransactionDAO transactionDAO = new TransactionDAO(connection, ledgerCategoryDAO, accountDAO, ledgerDAO);
            CategoryDAO categoryDAO = new CategoryDAO(connection);
            BudgetDAO budgetDAO = new BudgetDAO(connection);
            DebtPaymentDAO debtPaymentDAO = new DebtPaymentDAO(connection, transactionDAO);
            LoanTxLinkDAO loanTxLinkDAO = new LoanTxLinkDAO(connection, transactionDAO);
            BorrowingTxLinkDAO borrowingTxLinkDAO = new BorrowingTxLinkDAO(connection, transactionDAO);
            LendingTxLinkDAO lendingTxLinkDAO = new LendingTxLinkDAO(connection, transactionDAO);

            // create Business layer
            UserController userController = new UserController(userDAO);
            AccountController accountController = new AccountController(accountDAO, transactionDAO, debtPaymentDAO, loanTxLinkDAO, borrowingTxLinkDAO, lendingTxLinkDAO);
            LedgerController ledgerController = new LedgerController(ledgerDAO, transactionDAO, categoryDAO, ledgerCategoryDAO, accountDAO, budgetDAO);
            TransactionController transactionController = new TransactionController(transactionDAO, accountDAO, debtPaymentDAO, borrowingTxLinkDAO, loanTxLinkDAO, lendingTxLinkDAO);
            LedgerCategoryController ledgerCategoryController = new LedgerCategoryController(ledgerCategoryDAO, transactionDAO, budgetDAO);
            BudgetController budgetController = new BudgetController(budgetDAO, ledgerCategoryDAO);
            ReportController reportController = new ReportController(transactionDAO, accountDAO, budgetDAO, ledgerCategoryDAO);

            //  create CLI layer
            UserCLI userCLI = new UserCLI(userController, reportController);
            AccountCLI accountCLI = new AccountCLI(accountController, userController, reportController, transactionController, ledgerController);
            LedgerCLI ledgerCLI = new LedgerCLI(userController, reportController, ledgerController, transactionController, ledgerCategoryController, budgetController);
            TransactionCLI transactionCLI = new TransactionCLI(transactionController, userController, accountController, ledgerController, ledgerCategoryController);
            BudgetCLI budgetCLI = new BudgetCLI(budgetController, reportController, userController, ledgerController, ledgerCategoryController);
            BorrowingCLI borrowingCLI = new BorrowingCLI(accountController, userController, ledgerController);
            LendingCLI lendingCLI = new LendingCLI(accountController, userController, ledgerController);
            LedgerCategoryCLI ledgerCategoryCLI = new LedgerCategoryCLI(ledgerCategoryController, userController, ledgerController);

            // create MainCLI
            MainCLI mainCLI = new MainCLI(userCLI, accountCLI, ledgerCLI, transactionCLI, budgetCLI, ledgerCategoryCLI, borrowingCLI, lendingCLI);

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

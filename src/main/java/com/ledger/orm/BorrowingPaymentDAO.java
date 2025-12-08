package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.List;

public class BorrowingPaymentDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;
    public BorrowingPaymentDAO(Connection connection, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Account account, Transaction tx) {
        String sql = "INSERT INTO borrowing_payments (account_id, transaction_id) " +
                "VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            stmt.setLong(2, tx.getId());

            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (SQLException e) {
            System.err.println("SQL Exception during borrowing payment insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean isBorrowingPaymentTransaction(Transaction transaction) {
        String sql = "SELECT 1 FROM borrowing_payments WHERE transaction_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());

            try (var rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception checking borrowing payment status: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getTransactionByBorrowing(Account account) {
        List<Transaction> transactions = new java.util.ArrayList<>();

        String sql = "SELECT * FROM borrowing_payments WHERE account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long transactionId = rs.getLong("transaction_id");
                    Transaction tx = transactionDAO.getById(transactionId);
                    if (tx != null) {
                        transactions.add(tx);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception retrieving borrowing transactions: " + e.getMessage());
        }
        return transactions;
    }
}

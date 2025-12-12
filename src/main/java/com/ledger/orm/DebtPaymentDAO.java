package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DebtPaymentDAO {
    private final Connection connection;

    public DebtPaymentDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Account account, Transaction tx) {
        String sql = "INSERT INTO debt_payments (account_id, transaction_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            stmt.setLong(2, tx.getId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during debt payment insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean isDebtPaymentTransaction(Transaction transaction) {
        String sql = "SELECT 1 FROM debt_payments WHERE transaction_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception checking debt payment status: " + e.getMessage());
            return false;
        }
    }

}

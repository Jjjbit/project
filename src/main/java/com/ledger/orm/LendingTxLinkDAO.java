package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LendingTxLinkDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;
    public LendingTxLinkDAO(Connection connection, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Account account, Transaction tx) {
        String sql = "INSERT INTO lending_tx_link (account_id, transaction_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            stmt.setLong(2, tx.getId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during lending receiving insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean isLendingReceivingTransaction(Transaction transaction) {
        String sql = "SELECT 1 FROM lending_tx_link WHERE transaction_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception checking lending receiving payment status: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getTransactionByLending(Account account) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM lending_tx_link WHERE account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction tx = transactionDAO.getById(rs.getLong("transaction_id"));
                    if (tx != null) {
                        transactions.add(tx);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception retrieving lending receiving transactions: " + e.getMessage());
        }
        return transactions;
    }
}

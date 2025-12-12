package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LoanTxLinkDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;
    public LoanTxLinkDAO(Connection connection, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Account account, Transaction tx) {
        String sql = "INSERT INTO loan_tx_link (account_id, transaction_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            stmt.setLong(2, tx.getId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during loan payment insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean isLoanPaymentTransaction(Transaction transaction) {
        String sql = "SELECT 1 FROM loan_tx_link WHERE transaction_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception checking loan payment status: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getTransactionByLoan(Account account) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM loan_tx_link WHERE account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = transactionDAO.getById(rs.getLong("transaction_id"));
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during getTransactionByLoan: " + e.getMessage());
        }
        return transactions;
    }
}

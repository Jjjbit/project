package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DebtPaymentDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;

    public DebtPaymentDAO(Connection connection, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Account account, Transaction tx) {
        String sql = "INSERT INTO debt_payments (account_id, transaction_id) " +
                "VALUES (?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, account.getId());
            stmt.setLong(2, tx.getId());

            int affected = stmt.executeUpdate();
            return affected > 0;

        } catch (Exception e) {
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

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getTransactionsByCreditAccount(Account creditAccount) {
        List<Transaction> transactions = new ArrayList<>();

        String sql = "SELECT * FROM debt_payments WHERE account_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, creditAccount.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = transactionDAO.getById(rs.getLong("transaction_id"));
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getTransactionsByCreditAccount: " + e.getMessage());
        }
        return transactions;
    }

}

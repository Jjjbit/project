package com.ledger.orm;

import com.ledger.domain.Installment;
import com.ledger.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InstallmentPaymentDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;
    private final InstallmentDAO installmentDAO;

    public InstallmentPaymentDAO(Connection connection, TransactionDAO transactionDAO, InstallmentDAO installmentDAO) {
        this.installmentDAO = installmentDAO;
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Installment plan, Transaction tx) {
        String sql = "INSERT INTO installment_payments (installment_id, transaction_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, plan.getId());
            stmt.setLong(2, tx.getId());
            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during installment payment insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean isInstallmentPaymentTransaction(Transaction transaction) {
        String sql = "SELECT 1 FROM installment_payments WHERE transaction_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception checking installment payment status: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public Installment getInstallmentByTransaction(Transaction transaction) {
        String sql = "SELECT installment_id FROM installment_payments WHERE transaction_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transaction.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long installmentId = rs.getLong("installment_id");
                    return installmentDAO.getById(installmentId);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during getInstallmentByTransaction: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getTransactionsByInstallment(Installment installment) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM installment_payments WHERE installment_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, installment.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = transactionDAO.getById(rs.getLong("transaction_id"));
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getTransactionsByInstallment: " + e.getMessage());
        }
        return transactions;
    }
}

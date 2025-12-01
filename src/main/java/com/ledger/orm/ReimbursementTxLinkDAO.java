package com.ledger.orm;

import com.ledger.domain.Reimbursement;
import com.ledger.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReimbursementTxLinkDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;

    public ReimbursementTxLinkDAO(Connection connection, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(long reimbursementId, long transactionId) {
        String sql = "INSERT INTO reimbursement_tx_link (reimbursement_plan_id, reimbursement_transaction_id) " +
                "VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, reimbursementId);
            stmt.setLong(2, transactionId);

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public List<Transaction> getTransactionsByReimbursement(Reimbursement reimbursement) {
        List<Transaction> transactions = new ArrayList<>();

        String sql = "SELECT * FROM reimbursement_tx_link WHERE reimbursement_plan_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, reimbursement.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction transaction = transactionDAO.getById(rs.getLong("reimbursement_transaction_id"));
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getTransactionsByReimbursementId: " + e.getMessage());
        }
        return transactions;
    }

}

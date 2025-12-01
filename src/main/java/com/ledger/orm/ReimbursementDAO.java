package com.ledger.orm;

import com.ledger.domain.Ledger;
import com.ledger.domain.ReimbursableStatus;
import com.ledger.domain.Reimbursement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReimbursementDAO {
    private final Connection connection;
    private final TransactionDAO transactionDAO;

    public ReimbursementDAO(Connection connection, TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public List<Reimbursement> getByLedger(Ledger ledger) {
        String sql = "SELECT id, original_transaction_id, amount, reimbursement_status, remaining_amount FROM reimbursement_plan "
                + "WHERE ledger_id = ?";
        List<Reimbursement> reimbursements = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledger.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Reimbursement reimbursement = new Reimbursement();
                    reimbursement.setId(rs.getLong("id"));
                    //set original transaction
                    reimbursement.setOriginalTransaction(transactionDAO.getById(rs.getLong("original_transaction_id")));
                    reimbursement.setAmount(rs.getBigDecimal("amount"));
                    reimbursement.setStatus(ReimbursableStatus.valueOf(rs.getString("reimbursement_status")));
                    reimbursement.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                    reimbursements.add(reimbursement);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during getByLedger: " + e.getMessage());
        }
        return reimbursements;
    }

    @SuppressWarnings("SqlResolve")
    public Reimbursement getByOriginalTransactionId(long transactionId) {
        String sql = "SELECT id, original_transaction_id, amount, reimbursement_status, remaining_amount FROM reimbursement_plan WHERE original_transaction_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Reimbursement reimbursement = new Reimbursement();
                    reimbursement.setId(rs.getLong("id"));
                    //set original transaction
                    reimbursement.setOriginalTransaction(transactionDAO.getById(rs.getLong("original_transaction_id")));
                    reimbursement.setAmount(rs.getBigDecimal("amount"));
                    reimbursement.setStatus(ReimbursableStatus.valueOf(rs.getString("reimbursement_status")));
                    reimbursement.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                    return reimbursement;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during getByTransactionId: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Reimbursement claim) {
        String sql = "INSERT INTO reimbursement_plan (original_transaction_id, amount, reimbursement_status, ledger_id, remaining_amount) VALUES ( ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, claim.getOriginalTransaction().getId());
            stmt.setBigDecimal(2, claim.getAmount());
            stmt.setString(3, claim.getReimbursementStatus().name());
            stmt.setLong(4, claim.getLedger().getId());
            stmt.setBigDecimal(5, claim.getRemainingAmount());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        claim.setId(keys.getLong(1));
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Reimbursement claim) {
        String sql = "UPDATE reimbursement_plan SET amount = ?, reimbursement_status = ?, remaining_amount = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, claim.getAmount());
            stmt.setString(2, claim.getReimbursementStatus().name());
            stmt.setBigDecimal(3, claim.getRemainingAmount());
            stmt.setLong(4, claim.getId());

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during update: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Reimbursement claim) {
        String sql = "DELETE FROM reimbursement_plan WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, claim.getId());

            int affected = stmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.err.println("SQL Exception during delete: " + e.getMessage());
        }
        return false;
    }


}

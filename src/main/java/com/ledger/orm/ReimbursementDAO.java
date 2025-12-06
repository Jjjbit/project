package com.ledger.orm;

import com.ledger.domain.Ledger;
import com.ledger.domain.Reimbursement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReimbursementDAO {
    private final Connection connection;
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final AccountDAO accountDAO;

    public ReimbursementDAO(Connection connection, LedgerCategoryDAO ledgerCategoryDAO,
                            AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public List<Reimbursement> getByLedger(Ledger ledger) {
        String sql = "SELECT id, amount, is_ended, remaining_amount, from_account_id, ledger_category_id FROM reimbursement_plan "
                + "WHERE ledger_id = ?";
        List<Reimbursement> reimbursements = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledger.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Reimbursement reimbursement = new Reimbursement();
                    reimbursement.setId(rs.getLong("id"));
                    reimbursement.setAmount(rs.getBigDecimal("amount"));
                    reimbursement.setEnded(rs.getBoolean("is_ended"));
                    reimbursement.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                    //set category
                    reimbursement.setLedgerCategory(ledgerCategoryDAO.getById(rs.getLong("ledger_category_id")));
                    //set from account
                    reimbursement.setFromAccount(accountDAO.getAccountById(rs.getLong("from_account_id")));
                    reimbursement.setLedger(ledger);
                    reimbursements.add(reimbursement);
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during getByLedger: " + e.getMessage());
        }
        return reimbursements;
    }

    @SuppressWarnings("SqlResolve")
    public Reimbursement getById(long id) {
        String sql = "SELECT id, amount, is_ended, remaining_amount, from_account_id, ledger_category_id FROM reimbursement_plan WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Reimbursement reimbursement = new Reimbursement();
                    reimbursement.setId(rs.getLong("id"));
                    reimbursement.setAmount(rs.getBigDecimal("amount"));
                    reimbursement.setEnded(rs.getBoolean("is_ended"));
                    reimbursement.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                    //set category
                    reimbursement.setLedgerCategory(ledgerCategoryDAO.getById(rs.getLong("ledger_category_id")));
                    //set from account
                    reimbursement.setFromAccount(accountDAO.getAccountById(rs.getLong("from_account_id")));
                    return reimbursement;
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception during getById: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Reimbursement claim) {
        String sql = "INSERT INTO reimbursement_plan (amount, is_ended, ledger_id, remaining_amount, from_account_id, ledger_category_id) VALUES ( ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setBigDecimal(1, claim.getAmount());
            stmt.setBoolean(2, claim.isEnded());
            stmt.setLong(3, claim.getLedger().getId());
            stmt.setBigDecimal(4, claim.getRemainingAmount());
            stmt.setLong(5, claim.getFromAccount().getId());
            stmt.setLong(6, claim.getLedgerCategory().getId());

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
        String sql = "UPDATE reimbursement_plan SET amount = ?, is_ended = ?, remaining_amount = ?, ledger_category_id = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, claim.getAmount());
            stmt.setBoolean(2, claim.isEnded());
            stmt.setBigDecimal(3, claim.getRemainingAmount());
            stmt.setLong(4, claim.getLedgerCategory().getId());
            stmt.setLong(5, claim.getId());

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

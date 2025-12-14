package com.ledger.orm;

import com.ledger.domain.Budget;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;

import java.sql.*;

public class BudgetDAO {
    private final Connection connection;

    public BudgetDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public Budget getById(long budgetId) {
        String sql = "SELECT id, amount, period, category_id, ledger_id, start_date, end_date " +
                "FROM budgets WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, budgetId);
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Budget budget = new Budget();
                    budget.setId(rs.getLong("id"));
                    budget.setAmount(rs.getBigDecimal("amount"));
                    budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                    budget.setStartDate(rs.getDate("start_date").toLocalDate());
                    budget.setEndDate(rs.getDate("end_date").toLocalDate());
                    return budget;
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getById: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Budget budget) {
        String sql = "INSERT INTO budgets (amount, period, category_id, ledger_id, start_date, end_date) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setBigDecimal(1, budget.getAmount());
            stmt.setString(2, budget.getPeriod().name());
            if (budget.getCategory() != null) {
                stmt.setLong(3, budget.getCategory().getId());
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
            stmt.setLong(4, budget.getLedger().getId());
            stmt.setDate(5, Date.valueOf(budget.getStartDate()));
            stmt.setDate(6, Date.valueOf(budget.getEndDate()));
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        budget.setId(keys.getLong(1));
                        return true;
                    }
                }
            }
            return false;
        }catch (SQLException e){
            System.err.println("SQL Exception during insert: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Budget budget) {
        String sql = "UPDATE budgets SET amount = ?, start_date=?, end_date=? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, budget.getAmount());
            stmt.setDate(2, Date.valueOf(budget.getStartDate()));
            stmt.setDate(3, Date.valueOf(budget.getEndDate()));
            stmt.setLong(4, budget.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during update: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public Budget getBudgetByCategory(LedgerCategory category, Budget.Period p) {
        String sql = "SELECT id, amount, period, category_id, start_date, end_date " +
                "FROM budgets " +
                "WHERE category_id = ? AND period = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, category.getId());
            stmt.setString(2, p.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Budget budget = new Budget();
                    budget.setId(rs.getLong("id"));
                    budget.setAmount(rs.getBigDecimal("amount"));
                    budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                    budget.setStartDate(rs.getDate("start_date").toLocalDate());
                    budget.setEndDate(rs.getDate("end_date").toLocalDate());
                    budget.setCategory(category);
                    return budget;
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getBudgetByCategoryId: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve") //get budget for a ledger
    public Budget getBudgetByLedger(Ledger ledger, Budget.Period p) {
        String sql = "SELECT id, amount, period, category_id, start_date, end_date " +
                "FROM budgets " +
                "WHERE category_id IS NULL AND ledger_id = ? AND period = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledger.getId());
            stmt.setString(2, p.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Budget budget = new Budget();
                    budget.setId(rs.getLong("id"));
                    budget.setAmount(rs.getBigDecimal("amount"));
                    budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                    budget.setStartDate(rs.getDate("start_date").toLocalDate());
                    budget.setEndDate(rs.getDate("end_date").toLocalDate());
                    budget.setLedger(ledger);
                    return budget;
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getBudgetByLedgerId: " + e.getMessage());
        }
        return null;
    }
}




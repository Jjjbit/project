package com.ledger.orm;

import com.ledger.domain.Budget;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BudgetDAO {
    private final Connection connection;

    public BudgetDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public Budget getById(Long budgetId) throws SQLException {
        String sql = "SELECT id, amount, period, category_id, ledger_id " +
                "FROM budgets WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, budgetId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Budget budget = new Budget();
                budget.setId(rs.getLong("id"));
                budget.setAmount(rs.getBigDecimal("amount"));
                budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                return budget;
            }

        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Budget budget) throws SQLException {
        String sql = "INSERT INTO budgets (amount, period, category_id, ledger_id) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setBigDecimal(1, budget.getAmount());
            stmt.setString(2, budget.getPeriod().name());
            if (budget.getCategory() != null) {
                stmt.setLong(3, budget.getCategory().getId());
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
            stmt.setLong(4, budget.getLedger().getId());

            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        budget.setId(keys.getLong(1));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Budget budget) throws SQLException {
        String sql = "UPDATE budgets SET amount = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, budget.getAmount());
            stmt.setLong(2, budget.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve") //get active categorized budgets
    public List<Budget> getActiveBudgetsByCategoryId(Long categoryId, Budget.Period p) throws SQLException {
        List<Budget> budgets = new ArrayList<>();
        String sql = "SELECT id, amount, period, category_id " +
                "FROM budgets " +
                "WHERE category_id = ? ";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, categoryId);
            stmt.setString(2, p.name());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Budget budget = new Budget();
                budget.setId(rs.getLong("id"));
                budget.setAmount(rs.getBigDecimal("amount"));
                budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                budgets.add(budget);
            }
        }
        return budgets;
    }

    @SuppressWarnings("SqlResolve") //get all active budgets for a ledger
    public List<Budget> getActiveBudgetsByLedgerId(Long ledgerId, Budget.Period p) throws SQLException {
        List<Budget> budgets = new ArrayList<>();
        String sql = "SELECT id, amount, period, category_id" +
                "FROM budgets WHERE ledger_id = ? " +
                "AND period = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            stmt.setString(2, p.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Budget budget = new Budget();
                    budget.setId(rs.getLong("id"));
                    budget.setAmount(rs.getBigDecimal("amount"));
                    budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                    budgets.add(budget);
                }
            }
        }
        return budgets;
    }
    @SuppressWarnings("SqlResolve") //get active ledger-level budgets (without category)
    public List<Budget> getActiveLedgerLevelBudgets(Long ledgerId, Budget.Period p) throws SQLException {
        List<Budget> budgets = new ArrayList<>();
        String sql = "SELECT id, amount, period, category_id, start_date, end_date " +
                "FROM budgets WHERE ledger_id = ? AND category_id IS NULL AND start_date <= CURRENT_DATE AND end_date >= CURRENT_DATE " +
                "AND period = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            stmt.setString(2, p.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Budget budget = new Budget();
                    budget.setId(rs.getLong("id"));
                    budget.setAmount(rs.getBigDecimal("amount"));
                    budget.setPeriod(Budget.Period.valueOf(rs.getString("period")));
                    budget.setStartDate(rs.getDate("start_date").toLocalDate());
                    budget.setEndDate(rs.getDate("end_date").toLocalDate());
                    budgets.add(budget);
                }
            }
        }
        return budgets;
    }

}




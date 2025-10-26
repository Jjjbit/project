package com.ledger.orm;

import com.ledger.domain.InstallmentPlan;

import java.sql.*;

public class InstallmentPlanDAO {
    private Connection connection;

    public InstallmentPlanDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(InstallmentPlan plan) throws SQLException {
        String sql = "INSERT INTO installment_plan (linked_account_id, total_amount, plan_remaining_amount," +
                " total_periods, paid_periods, fee_rate, fee_strategy, repayment_start_date, name, category_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (var stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, plan.getLinkedAccount().getId());
            stmt.setBigDecimal(2, plan.getTotalAmount());
            stmt.setBigDecimal(3, plan.getRemainingAmount());
            stmt.setInt(4, plan.getTotalPeriods());
            stmt.setInt(5, plan.getPaidPeriods());
            stmt.setBigDecimal(6, plan.getFeeRate());
            stmt.setString(7, plan.getFeeStrategy().name());
            stmt.setDate(8, Date.valueOf(plan.getRepaymentStartDate()));
            stmt.setString(9, plan.getName());
            stmt.setLong(10, plan.getCategory().getId());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        plan.setId(keys.getLong(1));
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(InstallmentPlan plan) throws SQLException {
        String sql = "DELETE FROM installment_plan WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, plan.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(InstallmentPlan plan) throws SQLException {
        String sql = "UPDATE installment_plan SET " +
                "total_amount = ?, plan_remaining_amount = ?, total_periods = ?, paid_periods = ?, " +
                "fee_rate = ?, fee_strategy = ?, repayment_start_date = ?, name = ? " +
                "WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, plan.getTotalAmount());
            stmt.setBigDecimal(2, plan.getRemainingAmount());
            stmt.setInt(3, plan.getTotalPeriods());
            stmt.setInt(4, plan.getPaidPeriods());
            stmt.setBigDecimal(5, plan.getFeeRate());
            stmt.setString(6, plan.getFeeStrategy().name());
            stmt.setDate(7, Date.valueOf(plan.getRepaymentStartDate()));
            stmt.setString(8, plan.getName());
            stmt.setLong(9, plan.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean deleteByAccountId(Long accountId) throws SQLException {
        String sql = "DELETE FROM installment_plan WHERE linked_account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, accountId);
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public InstallmentPlan getById(Long id) throws SQLException {
        String sql = "SELECT id, linked_account_id, total_amount, plan_remaining_amount, total_periods, " +
                "paid_periods, fee_rate, fee_strategy, repayment_start_date, name, category_id " +
                "FROM installment_plan WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    InstallmentPlan plan = new InstallmentPlan();
                    plan.setId(rs.getLong("id"));
                    // Note: linkedAccount should be set separately after fetching the Account object
                    plan.setTotalAmount(rs.getBigDecimal("total_amount"));
                    plan.setRemainingAmount(rs.getBigDecimal("plan_remaining_amount"));
                    plan.setTotalPeriods(rs.getInt("total_periods"));
                    plan.setPaidPeriods(rs.getInt("paid_periods"));
                    plan.setFeeRate(rs.getBigDecimal("fee_rate"));
                    plan.setFeeStrategy(InstallmentPlan.FeeStrategy.valueOf(rs.getString("fee_strategy")));
                    plan.setRepaymentStartDate(rs.getDate("repayment_start_date").toLocalDate());
                    plan.setName(rs.getString("name"));
                    return plan;
                }
            }
        }
        return null;
    }

}

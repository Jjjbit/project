package com.ledger.orm;

import com.ledger.domain.Installment;

import java.sql.*;
import java.util.List;

public class InstallmentDAO {
    private final Connection connection;
    private final LedgerCategoryDAO categoryDAO;

    public InstallmentDAO(Connection connection, LedgerCategoryDAO categoryDAO) {
        this.categoryDAO = categoryDAO;
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(Installment plan) {
        String sql = "INSERT INTO installment (linked_account_id, total_amount, plan_remaining_amount," +
                " total_periods, paid_periods, interest, strategy, repayment_start_date, name, category_id, included_in_current_debt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, plan.getLinkedAccount().getId());
            stmt.setBigDecimal(2, plan.getTotalAmount());
            stmt.setBigDecimal(3, plan.getRemainingAmount());
            stmt.setInt(4, plan.getTotalPeriods());
            stmt.setInt(5, plan.getPaidPeriods());
            stmt.setBigDecimal(6, plan.getInterest());
            stmt.setString(7, plan.getStrategy().name());
            stmt.setDate(8, Date.valueOf(plan.getRepaymentStartDate()));
            stmt.setString(9, plan.getName());
            stmt.setLong(10, plan.getCategory().getId());
            stmt.setBoolean(11, plan.isIncludedInCurrentDebts());
            int affected = stmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        plan.setId(keys.getLong(1));
                        return true;
                    }
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during installment insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(Installment plan) {
        String sql = "DELETE FROM installment WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, plan.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during installment delete: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(Installment plan) {
        String sql = "UPDATE installment SET " +
                "total_amount = ?, plan_remaining_amount = ?, total_periods = ?, paid_periods = ?, " +
                "interest = ?, strategy = ?, repayment_start_date = ?, name = ? , included_in_current_debt = ? " +
                "WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setBigDecimal(1, plan.getTotalAmount());
            stmt.setBigDecimal(2, plan.getRemainingAmount());
            stmt.setInt(3, plan.getTotalPeriods());
            stmt.setInt(4, plan.getPaidPeriods());
            stmt.setBigDecimal(5, plan.getInterest());
            stmt.setString(6, plan.getStrategy().name());
            stmt.setDate(7, Date.valueOf(plan.getRepaymentStartDate()));
            stmt.setString(8, plan.getName());
            stmt.setBoolean(9, plan.isIncludedInCurrentDebts());
            stmt.setLong(10, plan.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during installment update: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public Installment getById(Long id) {
        String sql = "SELECT id, linked_account_id, total_amount, plan_remaining_amount, total_periods, " +
                "paid_periods, interest, strategy, repayment_start_date, name, category_id, included_in_current_debt " +
                "FROM installment WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Installment plan = new Installment();
                    plan.setId(rs.getLong("id"));
                    plan.setTotalAmount(rs.getBigDecimal("total_amount"));
                    plan.setRemainingAmount(rs.getBigDecimal("plan_remaining_amount"));
                    plan.setTotalPeriods(rs.getInt("total_periods"));
                    plan.setPaidPeriods(rs.getInt("paid_periods"));
                    plan.setInterest(rs.getBigDecimal("interest"));
                    plan.setStrategy(Installment.Strategy.valueOf(rs.getString("strategy")));
                    plan.setRepaymentStartDate(rs.getDate("repayment_start_date").toLocalDate());
                    plan.setName(rs.getString("name"));
                    plan.setIncludedInCurrentDebts(rs.getBoolean("included_in_current_debt"));

                    plan.setCategory(categoryDAO.getById(rs.getLong("category_id")));

                    return plan;
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during installment getById: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public List<Installment> getByAccountId(Long id) {
        String sql = "SELECT id, linked_account_id, total_amount, plan_remaining_amount, total_periods, " +
                "paid_periods, interest, strategy, repayment_start_date, name, category_id, included_in_current_debt " +
                "FROM installment WHERE linked_account_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Installment> plans = new java.util.ArrayList<>();
                while (rs.next()) {
                    Installment plan = new Installment();
                    plan.setId(rs.getLong("id"));
                    plan.setTotalAmount(rs.getBigDecimal("total_amount"));
                    plan.setRemainingAmount(rs.getBigDecimal("plan_remaining_amount"));
                    plan.setTotalPeriods(rs.getInt("total_periods"));
                    plan.setPaidPeriods(rs.getInt("paid_periods"));
                    plan.setInterest(rs.getBigDecimal("interest"));
                    plan.setStrategy(Installment.Strategy.valueOf(rs.getString("strategy")));
                    plan.setRepaymentStartDate(rs.getDate("repayment_start_date").toLocalDate());
                    plan.setName(rs.getString("name"));
                    plan.setIncludedInCurrentDebts(rs.getBoolean("included_in_current_debt"));

                    plan.setCategory(categoryDAO.getById(rs.getLong("category_id")));
                    plans.add(plan);
                }
                return plans;
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during installment getByAccountId: " + e.getMessage());
        }
        return null;
    }

}

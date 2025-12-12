package com.ledger.orm;

import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LedgerCategoryDAO {
    private final Connection connection;
    private final LedgerDAO ledgerDAO;

    public LedgerCategoryDAO(Connection connection, LedgerDAO ledgerDAO) {
        this.connection = connection;
        this.ledgerDAO = ledgerDAO;
    }

    @SuppressWarnings("SqlResolve")
    public LedgerCategory getById(long id) {
        String sql = "SELECT id, name, type, ledger_id FROM ledger_categories WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LedgerCategory category = new LedgerCategory();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    category.setLedger(ledgerDAO.getById(rs.getLong("ledger_id")));
                    return category;
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getById: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(LedgerCategory category) {
        String sql = "INSERT INTO ledger_categories (name, type, ledger_id, parent_id) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, category.getName());
            stmt.setString(2, category.getType().name());
            stmt.setLong(3, category.getLedger().getId());
            if (category.getParent() != null) {
                stmt.setLong(4, category.getParent().getId());
            } else {
                stmt.setNull(4, java.sql.Types.BIGINT);
            }

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        category.setId(generatedKeys.getLong(1));
                        return true;
                    }
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during insert: " + e.getMessage());
        }
        return false;
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(LedgerCategory category) {
        String sql = "UPDATE ledger_categories SET name = ?, parent_id = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, category.getName());
            if (category.getParent() != null) {
                stmt.setLong(2, category.getParent().getId());
            } else {
                stmt.setNull(2, Types.BIGINT);
            }
            stmt.setLong(3, category.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during update: " + e.getMessage());
            return false;
        }
    }

    public List<LedgerCategory> getTreeByLedgerId(long ledgerId) {
        List<LedgerCategory> categoriesNullParent = getCategoriesNullParent(ledgerId);
        return buildCategoryTree(categoriesNullParent);
    }

    @SuppressWarnings("SqlResolve")
    public List<LedgerCategory> getCategoriesNullParent(long ledgerId) {
        List<LedgerCategory> categories = new ArrayList<>();
        String sql = "SELECT id, name, parent_id, type, ledger_id FROM ledger_categories WHERE parent_id IS NULL AND ledger_id = ? " +
                "ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            try(ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LedgerCategory category = new LedgerCategory();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    category.setParent(null);
                    category.setLedger(ledgerDAO.getById(rs.getLong("ledger_id")));
                    categories.add(category);
                }
            }
        } catch (SQLException e){
            System.err.println("SQL Exception during getCategoriesNullParent: " + e.getMessage());
        }
        return categories;
    }

    @SuppressWarnings("SqlResolve")
    public List<LedgerCategory> getCategoriesByParentId(long parentId) {
        List<LedgerCategory> categories = new ArrayList<>();
        String sql = "SELECT id, name, parent_id, type, ledger_id FROM ledger_categories WHERE parent_id = ? ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LedgerCategory category = new LedgerCategory();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    category.setLedger( ledgerDAO.getById(rs.getLong("ledger_id")));
                    categories.add(category);
                }
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during getCategoriesByParentId: " + e.getMessage());
        }
        return categories;
    }


    private List<LedgerCategory> buildCategoryTree(List<LedgerCategory> categories) {
        List<LedgerCategory> allCategories = new ArrayList<>();

        for (LedgerCategory category : categories) {
            List<LedgerCategory> children = getCategoriesByParentId(category.getId());
            allCategories.add(category);
            for (LedgerCategory child : children) {
                child.setParent(category);
                allCategories.add(child);
            }
        }
        return allCategories;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(LedgerCategory category) {
        String sql = "DELETE FROM ledger_categories WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, category.getId());
            return stmt.executeUpdate() > 0;
        }catch (SQLException e){
            System.err.println("SQL Exception during delete: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public LedgerCategory getByNameAndLedger(String name, Ledger ledger) {
        String sql = "SELECT id, name, type, ledger_id, parent_id FROM ledger_categories WHERE name = ? AND ledger_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setLong(2, ledger.getId());
            try(ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    LedgerCategory category = new LedgerCategory();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    category.setLedger(ledger);
                    return category;
                }
            }
            return null;
        }catch (SQLException e){
            System.err.println("SQL Exception during getByNameAndLedger: " + e.getMessage());
            return null;
        }
    }
}

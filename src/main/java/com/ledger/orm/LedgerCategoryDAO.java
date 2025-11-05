package com.ledger.orm;

import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LedgerCategoryDAO {
    private final Connection connection;

    public LedgerCategoryDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public LedgerCategory getById(Long id) throws SQLException {
        String sql = "SELECT id, name, type FROM ledger_categories WHERE id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                LedgerCategory category = new LedgerCategory();
                category.setId(rs.getLong("id"));
                category.setName(rs.getString("name"));
                category.setType(CategoryType.valueOf(rs.getString("type")));
                return category;
            }
            return null;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean insert(LedgerCategory category) throws SQLException {
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
            return false;
        }
    }

    @SuppressWarnings("SqlResolve")
    public boolean update(LedgerCategory category) throws SQLException {
        String sql = "UPDATE ledger_categories SET name = ?, type = ?, ledger_id = ?, parent_id = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, category.getName());
            stmt.setString(2, category.getType().name());
            stmt.setLong(3, category.getLedger().getId());
            if (category.getParent() != null) {
                stmt.setLong(4, category.getParent().getId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }
            stmt.setLong(5, category.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    //not set parent and children
    @SuppressWarnings("SqlResolve")
    public List<LedgerCategory> getByLedgerId(Long ledgerId) throws SQLException {
        String sql = "SELECT id, name, type FROM ledger_categories WHERE ledger_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            ResultSet rs = stmt.executeQuery();
            List<LedgerCategory> categories = new ArrayList<>();
            while (rs.next()) {
                LedgerCategory category = new LedgerCategory();
                category.setId(rs.getLong("id"));
                category.setName(rs.getString("name"));
                category.setType(CategoryType.valueOf(rs.getString("type")));
                categories.add(category);
            }
            return categories;
        }
    }

    //set parent and children
    public List<LedgerCategory> getTreeByLedgerId(Long ledgerId) throws SQLException {
        List<LedgerCategory> categoriesNullParent = getCategoriesNullParent(ledgerId);
        return buildCategoryTree(categoriesNullParent);
    }

    @SuppressWarnings("SqlResolve")
    public List<LedgerCategory> getCategoriesNullParent(Long ledgerId) throws SQLException {
        List<LedgerCategory> categories = new ArrayList<>();

        String sql = "SELECT id, name, parent_id, type, ledger_id " +
                "FROM ledger_categories " +
                "WHERE parent_id IS NULL AND ledger_id = ? " +
                "ORDER BY id";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                LedgerCategory category = new LedgerCategory();
                category.setId(rs.getLong("id"));
                category.setName(rs.getString("name"));
                category.setType(CategoryType.valueOf(rs.getString("type")));
                category.setParent(null);
                categories.add(category);
            }
        }

        return categories;
    }
    @SuppressWarnings("SqlResolve")
    public List<LedgerCategory> getCategoriesByParentId(Long parentId) throws SQLException {
        List<LedgerCategory> categories = new ArrayList<>();

        String sql = "SELECT id, name, parent_id, type FROM ledger_categories WHERE parent_id = ? ORDER BY id";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LedgerCategory category = new LedgerCategory();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    categories.add(category);
                }
            }
        }
        return categories;
    }


    private List<LedgerCategory> buildCategoryTree(List<LedgerCategory> categories) throws SQLException {
        List<LedgerCategory> rootCategories = new ArrayList<>();

        for (LedgerCategory category : categories) {
            List<LedgerCategory> children = getCategoriesByParentId(category.getId());
            category.setChildren(children);
            rootCategories.add(category);
            for (LedgerCategory child : children) {
                child.setParent(category);
                rootCategories.add(child);
            }
        }

        return rootCategories;
    }

    @SuppressWarnings("SqlResolve")
    public boolean delete(LedgerCategory category) throws SQLException {
        String sql = "DELETE FROM ledger_categories WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, category.getId());
            return stmt.executeUpdate() > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public int countCategoriesInLedger(Long ledgerId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM ledger_categories WHERE ledger_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public int countCategoryInDatabase() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM ledger_categories";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        }
    }
    @SuppressWarnings("SqlResolve")
    public LedgerCategory getByNameAndLedger(String name, Ledger ledger) throws SQLException {
        String sql = "SELECT id, name, type, ledger_id, parent_id FROM ledger_categories WHERE name = ? AND ledger_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setLong(2, ledger.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                LedgerCategory category = new LedgerCategory();
                category.setId(rs.getLong("id"));
                category.setName(rs.getString("name"));
                category.setType(CategoryType.valueOf(rs.getString("type")));
                category.setLedger(ledger);
                return category;
            }
            return null;
        }
    }
}

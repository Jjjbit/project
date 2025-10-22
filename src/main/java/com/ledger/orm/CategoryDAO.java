package com.ledger.orm;

import com.ledger.domain.CategoryType;
import com.ledger.domain.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryDAO {
    private Connection connection;

    public CategoryDAO() throws SQLException {
        this.connection = ConnectionManager.getConnection();
    }

    public List<Category> getCategoryTree() throws SQLException {
        List<Category> allCategories = getAllCategories();
        return buildCategoryTree(allCategories);
    }

    @SuppressWarnings("SqlResolve")
    public List<Category> getAllCategories() throws SQLException {
        List<Category> categories = new ArrayList<>();

        String sql = "SELECT id, name, parent_id, type FROM global_categories ORDER BY id";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Category category = mapResultSetToGlobalCategory(rs);
                categories.add(category);
            }
        }
        return categories;
    }

    private List<Category> buildCategoryTree(List<Category> categories) {
        List<Category> rootCategories = new ArrayList<>();
        Map<Long, Category> categoryMap = new HashMap<>();

        // Initialize map
        for (Category category : categories) {
            category.setChildren(new ArrayList<>());
            categoryMap.put(category.getId(), category);
        }

        //
        for (Category category : categories) {
            if (category.getParent() != null) {
                Category parent = categoryMap.get(category.getParent().getId());
                if (parent != null) {
                    parent.getChildren().add(category);
                }
            } else {
                rootCategories.add(category);
            }
        }

        return rootCategories;
    }

    @SuppressWarnings("SqlResolve")
    public boolean copyGlobalCategoriesToLedger(Long ledgerId) throws SQLException {
        String sql = "INSERT INTO ledger_categories (ledger_id, name, parent_id, type) " +
                "SELECT ?, name, parent_id, type FROM global_categories";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, ledgerId);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    @SuppressWarnings("SqlResolve")
    public List<Category> getChildrenByParentId(Long parentId) throws SQLException {
        List<Category> categories = new ArrayList<>();
        String sql = "SELECT id, name, parent_id, type FROM global_categories WHERE parent_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, parentId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    categories.add(mapResultSetToGlobalCategory(rs));
                }
            }
        }
        return categories;
    }
    private Category mapResultSetToGlobalCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getLong("id"));
        category.setName(rs.getString("name"));
        category.setType(CategoryType.valueOf(rs.getString("type")));

        // Set parent if exists
        long parentId = rs.getLong("parent_id");
        if (!rs.wasNull()) {
            Category parent = new Category();
            parent.setId(parentId);
            category.setParent(parent);
        }

        return category;
    }

}

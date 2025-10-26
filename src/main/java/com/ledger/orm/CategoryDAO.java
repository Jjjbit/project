package com.ledger.orm;

import com.ledger.domain.Category;
import com.ledger.domain.CategoryType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {
    private Connection connection;

    public CategoryDAO(Connection connection) {
        this.connection = connection;
    }

    public List<Category> getCategoryTree() throws SQLException {
        List<Category> categoriesNullParent = getCategoriesNullParent();
        return buildCategoryTree(categoriesNullParent);
    }

    @SuppressWarnings("SqlResolve")
    public List<Category> getCategoriesNullParent() throws SQLException {
        List<Category> categories = new ArrayList<>();

        String sql = "SELECT id, name, parent_id, type FROM global_categories WHERE parent_id IS NULL ORDER BY id";

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Category category = new Category();
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
    private List<Category> getCategoriesByParentId(Long parentId) throws SQLException {
        List<Category> categories = new ArrayList<>();

        String sql = "SELECT id, name, parent_id, type FROM global_categories WHERE parent_id = ? ORDER BY id";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Category category = new Category();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    categories.add(category);
                }
            }
        }
        return categories;
    }

    private List<Category> buildCategoryTree(List<Category> categories) throws SQLException {
        List<Category> rootCategories = new ArrayList<>();

        for (Category category : categories) {
            List<Category> children = getCategoriesByParentId(category.getId());
            category.getChildren().addAll(children);
            rootCategories.add(category);
            for (Category child : children) {
                child.setParent(category);
                rootCategories.add(child);
            }
        }

        return rootCategories;
    }

    private Category mapResultSetToGlobalCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getLong("id"));
        category.setName(rs.getString("name"));
        category.setType(CategoryType.valueOf(rs.getString("type")));

        return category;
    }

}

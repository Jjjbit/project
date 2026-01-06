package com.ledger.ORM;

import com.ledger.DomainModel.Category;
import com.ledger.DomainModel.CategoryType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {
    private final Connection connection;

    public CategoryDAO(Connection connection) {
        this.connection = connection;
    }

    @SuppressWarnings("SqlResolve")
    public List<Category> getParentCategories() {
        List<Category> categories = new ArrayList<>();

        String sql = "SELECT id, name, parent_id, type FROM global_categories WHERE parent_id IS NULL ORDER BY id";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
             try(ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Category category = new Category();
                    category.setId(rs.getLong("id"));
                    category.setName(rs.getString("name"));
                    category.setType(CategoryType.valueOf(rs.getString("type")));
                    category.setParent(null);

                    categories.add(category);
                }
            }

        }catch (SQLException e){
            System.err.println("SQL Exception during getCategoriesNullParent: " + e.getMessage());
        }
        return categories;
    }

    @SuppressWarnings("SqlResolve")
    public List<Category> getCategoriesByParentId(long parentId) {
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
        }catch (SQLException e){
            System.err.println("SQL Exception during getCategoriesByParentId: " + e.getMessage());
        }
        return categories;
    }
}

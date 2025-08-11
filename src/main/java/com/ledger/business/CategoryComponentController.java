package com.ledger.business;

import com.ledger.domain.CategoryComponent;
import com.ledger.domain.SubCategory;
import com.ledger.orm.CategoryComponentDAO;
import jakarta.transaction.Transactional;

public class CategoryComponentController {
    private CategoryComponentDAO categoryComponentDAO;

    public CategoryComponentController(CategoryComponentDAO categoryComponentDAO) {
        this.categoryComponentDAO = categoryComponentDAO;
    }

    @Transactional
    public void createCategoryComponent(CategoryComponent category){
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        categoryComponentDAO.save(category);
    }

    @Transactional
    public void deleteCategoryComponent(Long categoryId) {
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        categoryComponentDAO.delete(categoryId);
    }

    //TODO
    @Transactional
    public void addSubCategory(Long parentCategoryId, CategoryComponent subCategory) {
        CategoryComponent parentCategory = categoryComponentDAO.findById(parentCategoryId);
        if (parentCategory == null) {
            throw new IllegalArgumentException("Parent category not found");
        }
        if (subCategory == null) {
            throw new IllegalArgumentException("Sub-category cannot be null");
        }
        parentCategory.add(subCategory);
        categoryComponentDAO.update(parentCategory);
    }

    //TODO
    @Transactional
    public void removeSubCategory(Long parentCategoryId, Long subCategoryId) {
        CategoryComponent parentCategory = categoryComponentDAO.findById(parentCategoryId);
        if (parentCategory == null) {
            throw new IllegalArgumentException("Parent category not found");
        }
        CategoryComponent subCategory = categoryComponentDAO.findById(subCategoryId);
        if (subCategory == null) {
            throw new IllegalArgumentException("Sub-category not found");
        }
        if (!parentCategory.getChildren().contains(subCategory)) {
            throw new IllegalArgumentException("Sub-category is not a child of the parent category");
        }
        parentCategory.remove(subCategory);
        categoryComponentDAO.update(parentCategory);
    }

    //TODO: changeLevel
    @Transactional
    public void demoteCategory(Long categoryId, CategoryComponent newParent) {
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        if (newParent == null) {
            throw new IllegalArgumentException("New parent cannot be null");
        }
        category.changeLevel(newParent);
        categoryComponentDAO.update(category);
    }

    @Transactional
    public void promoteCategory(Long categoryId, CategoryComponent root) {
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        if (root == null) {
            throw new IllegalArgumentException("Root cannot be null");
        }
        category.changeLevel(root);
        categoryComponentDAO.update(category);
    }

    @Transactional
    public void changeParentCategory(Long categoryId, CategoryComponent newParent) {
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        if (newParent == null) {
            throw new IllegalArgumentException("New parent cannot be null");
        }
        if(category instanceof SubCategory){
            ((SubCategory)category).changeParent(newParent);
            categoryComponentDAO.update(category);
        }
    }

    @Transactional
    public void changeName(Long categoryId, String newName) {
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("New name cannot be null or empty");
        }
        category.setName(newName);
        categoryComponentDAO.update(category);
    }

}

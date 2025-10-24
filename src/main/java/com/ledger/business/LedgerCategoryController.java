package com.ledger.business;

import com.ledger.domain.CategoryType;
import com.ledger.domain.Ledger;
import com.ledger.domain.LedgerCategory;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.LedgerCategoryDAO;
import com.ledger.orm.LedgerDAO;

import java.sql.SQLException;

public class LedgerCategoryController {
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final LedgerDAO ledgerDAO;
    private BudgetDAO budgetDAO;

    public LedgerCategoryController(LedgerCategoryDAO ledgerCategoryDAO,
                                    LedgerDAO ledgerDAO,
                                    BudgetDAO budgetDAO) {
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.ledgerDAO = ledgerDAO;
        this.budgetDAO = budgetDAO;
    }
    public LedgerCategory createCategory(String name, Ledger ledger, CategoryType type) throws SQLException {
        if(ledger == null){
            throw new IllegalArgumentException("Ledger cannot be null");
        }
        if(ledgerDAO.getById(ledger.getId()) != null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        if(name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
        if(ledgerCategoryDAO.getByNameAndLedger(name, ledger) != null) {
            throw new IllegalArgumentException("Category name already exists in this ledger");
        }
        LedgerCategory category = new LedgerCategory(name, type, ledger);
        ledgerCategoryDAO.insert(category);
        return category;
    }

    public LedgerCategory createSubCategory(String name, LedgerCategory parentCategory) throws SQLException {
        if(parentCategory == null){
            throw new IllegalArgumentException("Parent category cannot be null");
        }
        if(parentCategory.getParent()!=null){
            throw new IllegalArgumentException("Cannot add sub-category to a sub-category");
        }
        Ledger ledger = parentCategory.getLedger();
        if(ledgerDAO.getById(ledger.getId()) != null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        if(name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
        if(ledgerCategoryDAO.getByNameAndLedger(name, ledger) != null) {
            throw new IllegalArgumentException("Category name already exists in this ledger");
        }
        LedgerCategory category = new LedgerCategory(name, parentCategory.getType(), ledger);
        category.setParent(parentCategory);
        ledgerCategoryDAO.insert(category);
        return category;
    }

    public boolean promoteSubCategory(LedgerCategory subCategory) throws SQLException {
        if(subCategory == null){
            throw new IllegalArgumentException("Sub-category cannot be null");
        }
        if(subCategory.getParent() == null){
            throw new IllegalArgumentException("Category cannot be promoted because it is not a sub-category");
        }
        LedgerCategory parentCategory = subCategory.getParent();
        subCategory.setParent(null);
        parentCategory.getChildren().remove(subCategory);

        return ledgerCategoryDAO.update(subCategory); //update parent_id in database
    }
    public boolean demoteCategory(LedgerCategory category, LedgerCategory newParent) throws SQLException {
        if(category == null){
            throw new IllegalArgumentException("Category cannot be null");
        }
        if(newParent == null){
            throw new IllegalArgumentException("New parent category cannot be null");
        }
        if(newParent.getParent() != null){
            throw new IllegalArgumentException("New parent category cannot be a sub-category");
        }
        if(category.getId().equals(newParent.getId())){
            throw new IllegalArgumentException("Category cannot be its own parent");
        }
        if(category.getParent() != null){
            throw new IllegalArgumentException("Category is already a sub-category");
        }
        if(!category.getChildren().isEmpty()){
            throw new IllegalArgumentException("Category with sub-categories cannot be demoted");
        }
        if(category.getType() != newParent.getType()){
            throw new IllegalArgumentException("Category and new parent category must have the same type");
        }
        Ledger ledger = category.getLedger();
        if(!newParent.getLedger().getId().equals(ledger.getId())){
            throw new IllegalArgumentException("New parent category must belong to the same ledger");
        }
        category.setParent(newParent);
        newParent.getChildren().add(category);
        return ledgerCategoryDAO.update(category); //update parent_id in database
    }

    public boolean renameCategory(LedgerCategory category, String newName) throws SQLException {
        if(category == null){
            throw new IllegalArgumentException("Category cannot be null");
        }
        if(newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be empty");
        }
        Ledger ledger = category.getLedger();
        LedgerCategory existingCategory = ledgerCategoryDAO.getByNameAndLedger(newName, ledger);
        if(existingCategory != null && !existingCategory.getId().equals(category.getId())) {
            throw new IllegalArgumentException("Category name already exists in this ledger");
        }

        category.setName(newName);
        return ledgerCategoryDAO.update(category);
    }

    public boolean deleteCategory(LedgerCategory category) throws SQLException {
        if(category == null){
            throw new IllegalArgumentException("Category cannot be null");
        }
        if(ledgerCategoryDAO.getCategoryById(category.getId()) == null) {
            throw new IllegalArgumentException("Category not found");
        }
        if(!category.getChildren().isEmpty()){
            throw new IllegalArgumentException("Category with sub-categories cannot be deleted");
        }

        //delete budget
        //List<Budget> budgets = category.getBudgets();
        /*List<Budget> budgets = budgetDAO.getBudgetByCategoryId(category.getId());
        for(Budget budget : budgets){
            category.getBudgets().remove(budget);
            budget.setCategory(null);
            budgetDAO.delete(budget);
        }*/
        Ledger ledger = category.getLedger();
        ledger.getCategories().remove(category);
        return ledgerCategoryDAO.delete(category);
    }





}

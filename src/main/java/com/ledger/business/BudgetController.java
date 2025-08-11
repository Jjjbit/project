package com.ledger.business;

import com.ledger.domain.Budget;
import com.ledger.domain.CategoryComponent;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.CategoryComponentDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

public class BudgetController {
    private BudgetDAO budgetDAO;
    private CategoryComponentDAO categoryComponentDAO;

    public BudgetController(BudgetDAO budgetDAO, CategoryComponentDAO categoryComponentDAO) {
        this.budgetDAO = budgetDAO;
        this.categoryComponentDAO=categoryComponentDAO;
    }

    @Transactional
    public void createBudget(Budget budget) {
        if (budget == null) {
            throw new IllegalArgumentException("Budget cannot be null");
        }
        if (budget.getCategory() == null || budget.getCategory().getId() == null) {
            throw new IllegalArgumentException("Budget category cannot be null");
        }

        budgetDAO.save(budget);
    }

    @Transactional
    public void deleteBudget(Long budgetId){
        Budget budget=budgetDAO.findById(budgetId);
        if (budget == null) {
            throw new IllegalArgumentException("Budget not found");
        }
        budgetDAO.update(budget);
    }
    @Transactional
    public void updateBudget(Long budgetId, BigDecimal newAmount) {
        Budget budget = budgetDAO.findById(budgetId);
        if (budget == null) {
            throw new IllegalArgumentException("Budget not found");
        }
        budget.setAmount(newAmount);
        budgetDAO.update(budget);
    }

    //ritorna importo di budget di categoria/subcategoria
    public BigDecimal getBudgetByCategoryId(Long categoryId, Budget.BudgetPeriod period){
        CategoryComponent category= categoryComponentDAO.findById(categoryId);

        if(category==null){
            throw new IllegalArgumentException("Category not found");
        }
        return category.getTotalBudgetForPeriod(period);
    }
}

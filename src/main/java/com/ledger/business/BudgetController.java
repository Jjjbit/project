package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.LedgerCategoryDAO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BudgetController {
    private final BudgetDAO budgetDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;

    public BudgetController(BudgetDAO budgetDAO, LedgerCategoryDAO ledgerCategoryDAO) {
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.budgetDAO = budgetDAO;
    }

    public Budget getActiveBudgetByLedger(Ledger ledger, Period period) {
        Budget budget = budgetDAO.getBudgetByLedger(ledger, period);
        if(budget != null){
            budget.refreshIfExpired();
            budgetDAO.update(budget);
        }
        return budget;
    }

    public Budget getActiveBudgetByCategory(LedgerCategory category, Period period) {
        Budget budget = budgetDAO.getBudgetByCategory(category, period);
        if(budget != null){
            budget.refreshIfExpired();
            budgetDAO.update(budget);
        }
        return budget;
    }


    public boolean editBudget(Budget budget, BigDecimal newAmount) {
        if(budget == null || newAmount == null) {
            return false;
        }
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        budget.setAmount(newAmount);
        return budgetDAO.update(budget);
    }

    public boolean refreshBudget(Budget budget) {
        if(budget == null) {
            return false;
        }
        budget.refreshIfExpired();
        return budgetDAO.update(budget);
    }

    public boolean mergeBudgets(Budget targetBudget) {
        if (targetBudget == null) {
            return false;
        }
        refreshBudget(targetBudget);

        if (targetBudget.getCategory() != null) {
            if (targetBudget.getCategory().getParent() != null) {
                return false; //targetBudget's category must be a top-level category or null
            }
        }

        Ledger ledger = targetBudget.getLedger();
        if (targetBudget.getCategory() == null) { //merge category-level budget into ledger-level budget
            List<LedgerCategory> expenseCategories = ledgerCategoryDAO.getTreeByLedgerId(ledger.getId()).stream()
                    .filter(c -> c.getType().equals(CategoryType.EXPENSE)) //only expense categories
                    .filter(c -> c.getParent() == null) //only top-level categories
                    .toList();
            List<Budget> sourceBudgets = new ArrayList<>();
            for (LedgerCategory cat : expenseCategories) {
                Budget catBudget = budgetDAO.getBudgetByCategory(cat, targetBudget.getPeriod());
                if (catBudget != null) {
                    refreshBudget(catBudget);
                    sourceBudgets.add(catBudget); //add to source budgets to merge
                }
            }

            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));

        } else { //merge subcategory budget into category budget
            if (targetBudget.getCategory().getParent() != null) {
                return false; //targetBudget category must be a top-level category
            }
            List<LedgerCategory> subcategories = ledgerCategoryDAO.getCategoriesByParentId(targetBudget.getCategory().getId()).stream()
                    .filter(c -> c.getType().equals(CategoryType.EXPENSE)) //only expense categories
                    .toList();
            List<Budget> sourceBudgets = new ArrayList<>();
            for (LedgerCategory subcat : subcategories) {
                Budget subcatBudget = budgetDAO.getBudgetByCategory(subcat, targetBudget.getPeriod());
                if (subcatBudget != null) {
                    refreshBudget(subcatBudget);
                    sourceBudgets.add(subcatBudget);
                }
            }
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));
        }
        return budgetDAO.update(targetBudget);
    }
}




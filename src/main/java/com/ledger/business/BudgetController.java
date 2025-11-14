package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.LedgerCategoryDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BudgetController {
    private final BudgetDAO budgetDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final TransactionDAO transactionDAO;

    public BudgetController(BudgetDAO budgetDAO, LedgerCategoryDAO ledgerCategoryDAO,
                            TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.budgetDAO = budgetDAO;
    }

    public boolean editBudget(Budget budget, BigDecimal newAmount) {
        try {
            if (budget == null) {
                return false;
            }
            if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
                return false;
            }
            if (budgetDAO.getById(budget.getId()) == null) {
                return false;
            }
            budget.setAmount(newAmount);
            return budgetDAO.update(budget);
        } catch (SQLException e) {
            System.err.println("SQL Exception during editBudget: " + e.getMessage());
            return false;
        }
    }

    public boolean refreshBudget(Budget budget) {
        try {
            if (budget == null) {
                return false;
            }
            if (budgetDAO.getById(budget.getId()) == null) {
                return false;
            }
            budget.refreshIfExpired();
            return budgetDAO.update(budget);
        } catch (SQLException e) {
            System.err.println("SQL Exception during refreshBudget: " + e.getMessage());
            return false;
        }
    }

    public boolean mergeBudgets(Budget targetBudget) {
        try {
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
                    Budget catBudget = budgetDAO.getBudgetByCategoryId(cat.getId(), targetBudget.getPeriod());
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
                    Budget subcatBudget = budgetDAO.getBudgetByCategoryId(subcat.getId(), targetBudget.getPeriod());
                    if (subcatBudget != null) {
                        refreshBudget(subcatBudget);
                        sourceBudgets.add(subcatBudget);
                    }
                }
                BigDecimal mergedAmount = sourceBudgets.stream()
                        .map(Budget::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount).setScale(2, RoundingMode.HALF_UP));
            }

            return budgetDAO.update(targetBudget);
        } catch (SQLException e) {
            System.err.println("SQL Exception during mergeBudgets: " + e.getMessage());
            return false;
        }
    }

    //report
    public boolean isOverBudget(Budget budget) {

        refreshBudget(budget);
        Ledger ledger = budget.getLedger();

        try {
            if (budget.getCategory() == null) { //ledger-level budget
                List<Transaction> transactions = transactionDAO.getByLedgerId(ledger.getId()).stream()
                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                        .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                        .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                        .toList();
                BigDecimal totalExpenses = transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return totalExpenses.compareTo(budget.getAmount()) > 0;
            } else { //budget is a category-level budget
                if (!budget.getCategory().getLedger().equals(ledger)) {
                    return false;
                }
                List<Transaction> transactions = new ArrayList<>(transactionDAO.getByLedgerId(ledger.getId()).stream()
                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                        .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                        .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                        .filter(t -> t.getCategory() != null)
                        .filter(t -> t.getCategory().getId().equals(budget.getCategory().getId()))
                        .toList());
                LedgerCategory category= budget.getCategory();
                List<LedgerCategory> childCategories = ledgerCategoryDAO.getCategoriesByParentId(category.getId());
                for (LedgerCategory childCategory : childCategories) {
                    transactions.addAll(ledger.getTransactions().stream()
                            .filter(t -> t.getType() == TransactionType.EXPENSE)
                            .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                            .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                            .filter(t -> t.getCategory() != null)
                            .filter(t -> t.getCategory().getId().equals(childCategory.getId()))
                            .toList());
                }
                BigDecimal totalCategoryBudget = transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return totalCategoryBudget.compareTo(budget.getAmount()) > 0; //>0: over budget
            }
        }catch (SQLException e){
            System.err.println("SQL Exception in isOverBudget: " + e.getMessage());
            return false;
        }
    }
}




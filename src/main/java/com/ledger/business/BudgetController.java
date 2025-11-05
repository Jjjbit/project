package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.BudgetDAO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BudgetController {
    private final BudgetDAO budgetDAO;


    public BudgetController(BudgetDAO budgetDAO) {
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

    public boolean mergeBudgets(Budget targetBudget) {
        try {

            if (!targetBudget.isActive(LocalDate.now())) {
                targetBudget.refreshIfExpired(); //refresh budget if expired
                //targetBudget.setAmount(BigDecimal.ZERO); //reset amount
                //LocalDate newStartDate = targetBudget.calculateStartDateForPeriod(LocalDate.now(), targetBudget.getPeriod());
                //targetBudget.setStartDate(newStartDate);
                //LocalDate newEndDate = targetBudget.calculateEndDateForPeriod(newStartDate, targetBudget.getPeriod());
                //targetBudget.setEndDate(newEndDate);
            }

            if (targetBudget.getCategory() != null) {
                if (targetBudget.getCategory().getParent() != null) {
                    return false; //targetBudget's category must be a top-level category or null
                }
            }

            Ledger ledger = targetBudget.getLedger();
            if (targetBudget.getCategory() == null) { //merge category-level budget into ledger-level budget
                List<LedgerCategory> expenseCategories = ledger.getCategories().stream()
                        .filter(c -> c.getType().equals(CategoryType.EXPENSE)) //only expense categories
                        .filter(c -> c.getParent() == null) //only top-level categories
                        .toList();
                List<Budget> sourceBudgets = new ArrayList<>();
                for (LedgerCategory cat : expenseCategories) {
                    Budget catBudget = cat.getBudgets().stream()
                            .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //find budget for the same period
                            .findFirst()
                            .orElse(null);
                    if (catBudget != null) {
                        if (!catBudget.isActive(LocalDate.now())) { //if budget is expired, refresh it
                            catBudget.refreshIfExpired();
                        }
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
                List<LedgerCategory> subcategories = targetBudget.getCategory().getChildren();
                List<Budget> sourceBudgets = new ArrayList<>();
                for (LedgerCategory subcat : subcategories) {
                    Budget subcatBudget = subcat.getBudgets().stream()
                            .filter(b -> b.getPeriod() == targetBudget.getPeriod())
                            .findFirst()
                            .orElse(null);
                    if (subcatBudget != null) {
                        if (!subcatBudget.isActive(LocalDate.now())) {
                            subcatBudget.refreshIfExpired();
                        }
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
        if (!budget.isActive(LocalDate.now())) {
            try {
                budget.refreshIfExpired(); //refresh budget if expired
                budgetDAO.update(budget);
            }catch (SQLException e){
                System.err.println("SQL Exception during isOverBudget refresh: " + e.getMessage());
                return false;
            }
        }

        Ledger ledger = budget.getLedger();
        if (budget.getCategory() == null) { //ledger budget
            List<Transaction> transactions = ledger.getTransactions().stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                    .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                    .toList();
            BigDecimal totalExpenses = transactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalExpenses.compareTo(budget.getAmount()) > 0;
        } else { //budget is a category budget
            if (!budget.getCategory().getLedger().equals(ledger)) {
                    return false;
                }
                List<Transaction> transactions = new ArrayList<>(ledger.getTransactions().stream()
                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                        .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                        .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                        .filter(t -> t.getCategory() != null)
                        .filter(t -> t.getCategory().getId().equals(budget.getCategory().getId()))
                        .toList());
                List<LedgerCategory> childCategories = budget.getCategory().getChildren();
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
        }
}




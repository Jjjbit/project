package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.LedgerCategoryDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BudgetController {
    private final BudgetDAO budgetDAO;
    private final TransactionDAO transactionDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;


    public BudgetController(BudgetDAO budgetDAO, TransactionDAO transactionDAO, LedgerCategoryDAO ledgerCategoryDAO) {
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.transactionDAO = transactionDAO;
        this.budgetDAO = budgetDAO;
    }

    public Budget createBudget(BigDecimal amount, LedgerCategory category,
                               Budget.Period period, Ledger ledger) {
        try {

            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return null; // Invalid amount
            }
            if (ledger == null) {
                return null;
            }
            if (category != null) {
                if (category.getType().equals(CategoryType.INCOME)) {
                    return null; // Cannot create budget for income category
                }

                if (!budgetDAO.getActiveBudgetsByCategoryId(category.getId(), period).isEmpty()) {
                    return null; // Budget for this category and period already exists
                }
                if (!ledger.getCategories().contains(category)) {
                    return null; // Category does not belong to the ledger
                }
            } else {
                if (!budgetDAO.getActiveLedgerLevelBudgets(ledger.getId(), period).isEmpty()) {
                    return null; // Budget for this ledger and period already exists
                }
            }

            Budget budget = new Budget(amount, period, category, ledger);
            if (category != null) {
                category.getBudgets().add(budget);
            }
            ledger.getBudgets().add(budget);


            budgetDAO.insert(budget);
            return budget;
        } catch (SQLException e) {
            System.err.println("SQL Exception during createBudget: " + e.getMessage());
            return null;
        }
    }

    public boolean editBudget(Budget budget, BigDecimal newAmount) {
        try{
        if (budget == null) {
            return false;
        }
        if(newAmount.compareTo(BigDecimal.ZERO)<0){
            return  false;
        }
        if(budgetDAO.getById(budget.getId())==null){
            return false;
        }
        budget.setAmount(newAmount);
        return budgetDAO.update(budget);
        }catch (SQLException e){
            System.err.println("SQL Exception during editBudget: " + e.getMessage());
            return false;
        }
    }

    public boolean mergeBudgets(Budget targetBudget) {
        try{

        if (!targetBudget.isActive(LocalDate.now())) {
            return false;
        }
        if(targetBudget.getCategory() != null) {
            if (targetBudget.getCategory().getParent() != null) {
                return false; //targetBudget's category must be a top-level category or null
            }
        }

        Ledger ledger = targetBudget.getLedger();
        if (targetBudget.getCategory() == null) { //merge category budget into ledger budget
            List<Budget> sourceBudgets = ledger.getBudgets().stream() //get all active budgets of ledger for the same period
                    .filter(b -> b.getCategory() != null) //only budgets with category
                    .filter(b -> b.getCategory().getParent() == null) //top-level categories only
                    .filter(b -> b.isActive(LocalDate.now())) //active budgets only
                    .toList();
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));


        } else { //merge subcategory budget into category budget
            if (targetBudget.getCategory().getParent() != null) {
                return false; //targetBudget category must be a top-level category
            }
            List<Budget> sourceBudgets = targetBudget.getCategory().getChildren().stream() //tutte le subcategorie di targetBudget
                    .flatMap(c -> c.getBudgets().stream()) //tutti i budget di tutte le subcategorie di targetBudget
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //stesso periodo
                    .filter(b -> b.isActive(LocalDate.now())) //solo budget attivi
                    .toList();//tutti i budget di tutte le subcategorie di targetBudget non attivi in periodo di targetBudget
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount).setScale(2, RoundingMode.HALF_UP));
        }

        return budgetDAO.update(targetBudget);
        }catch (SQLException e){
            System.err.println("SQL Exception during mergeBudgets: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteBudget(Budget budget) {
        try{
        if(budget==null){
            return false;
        }
        if(budgetDAO.getById(budget.getId())==null){
            return false;
        }

        Ledger ledger=budget.getLedger();
        ledger.getBudgets().remove(budget);


        return budgetDAO.delete(budget);
        }catch (SQLException e){
            System.err.println("SQL Exception during deleteBudget: " + e.getMessage());
            return false;
        }
    }

    //report
    public boolean isOverBudget(Budget budget, Ledger ledger) {
        if (!budget.isActive(LocalDate.now())) {
            return false;
        }

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
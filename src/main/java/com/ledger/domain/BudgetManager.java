package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BudgetManager {
    private List<Budget> budgets;

    public BudgetManager(List<Budget> budgets) {
        this.budgets = budgets;
    }

    public List<Transaction> getTransactionsInPeriod(Budget.BudgetPeriod period, CategoryComponent category){
        List<Transaction> transactions = category.getTransactions().stream()
                .filter(t -> budgets.stream()
                        .anyMatch(b -> b.getCategory().equals(category) && b.isTransactionInPeriod(t, period)))
                .toList();
        return transactions;
    }

    //total budget for a user for a specific period and without category
    public BigDecimal getUserTotalBudget(User user, Budget.BudgetPeriod period) {
        return budgets.stream()
                //.filter(b -> b.getOwner().equals(user))
                .filter(b-> !b.isForCategory())
                .filter(b -> b.getPeriod().equals(period))
                .filter(b -> b.isInPeriod(LocalDate.now()))
                .map(Budget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //total budget for a user for a specific period and category
    public BigDecimal getCategoryBudgets(User user, Budget.BudgetPeriod period, CategoryComponent category) {
        return budgets.stream()
                //.filter(b -> b.getOwner().equals(user))
                .filter(b-> b.isForCategory())
                .filter(b -> category.equals(b.getCategory()))
                .filter(b -> b.getPeriod().equals(period))
                .filter(b -> b.isInPeriod(LocalDate.now()))
                .map(Budget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //total spending for a user for a specific period
    public BigDecimal getTotalSpendingForPeriod(User user, Budget.BudgetPeriod period) {
        return budgets.stream()
                //.filter(b -> b.getOwner().equals(user))
                .filter(b -> !b.isForCategory())
                .filter(b -> b.getPeriod().equals(period))
                .filter(b -> b.isInPeriod(LocalDate.now()))
                .flatMap(b -> b.getCategory().getTransactions().stream().filter(t-> t.type==TransactionType.EXPENSE))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Check if the user is over budget for a specific period and category
    public boolean isOverBudgetForCategory(User user, Budget.BudgetPeriod period, CategoryComponent category) {
        BigDecimal budgetAmount = getCategoryBudgets(user, period, category);
        BigDecimal spending =category.getTotalSpendingForPeriod(user, period);
        return spending.compareTo(budgetAmount) > 0;
    }

    // Check if the user is over budget for a specific period
    public boolean isOverBudget(User user, Budget.BudgetPeriod period) {
        BigDecimal budgetAmount = getUserTotalBudget(user, period);
        BigDecimal spending = getTotalSpendingForPeriod(user, period);
        return spending.compareTo(budgetAmount) > 0;
    }


}




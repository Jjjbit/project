package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
public abstract class CategoryComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    protected String name;

    @Column(length = 20, nullable = false)
    protected String type; //"income", "expense", "root"

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    protected List<Transaction> transactions = new ArrayList<>(); //un category -> più transazioni.

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Budget> budgets = new ArrayList<>();

    public CategoryComponent() {}
    public CategoryComponent(String name, String type) {
        this.name = name;
        this.type = type;
    }
    public abstract void remove(CategoryComponent child);
    public abstract void add(CategoryComponent child);
    public abstract List<CategoryComponent> getChildren();
    public abstract void display(String indent);
    public abstract CategoryComponent getParent();
    public String getName() {
        return name;
    }
    public String getType() {
        return type;
    }
    //public abstract void changeLevel(CategoryComponent root);

    public void addTransaction(Transaction t) {
        transactions.add(t);
    }
    public void addBudget(Budget b) {
        budgets.add(b);
    }

    //returns list of transactions of the category and its subcategories in period
    public List<Transaction> getTransactionsInPeriod(Budget.BudgetPeriod period, LocalDate startDate) {
        return transactions.stream()
                .filter(t -> switch (period) {
                    case MONTHLY -> t.getDate().isBefore(startDate.plusMonths(1));
                    case WEEKLY -> t.getDate().isBefore(startDate.plusWeeks(1));
                    case YEARLY -> t.getDate().isBefore(startDate.plusYears(1));
                })
                .toList();
    }

    //returns list of budget for the category and its subcategories in period
    public List<Budget> getBudgetsForPeriod(Budget.BudgetPeriod p) {
        return budgets.stream()
                .filter(b -> b.getPeriod() == p)
                .toList();
    }

    //returns total budget for the category and its subcategories in period
    public BigDecimal getTotalBudgetForPeriod(Budget.BudgetPeriod period) {
        return budgets.stream()
                .filter(b -> b.getPeriod() == period)
                .map(Budget::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public abstract List<Transaction> getTransactions();

    public abstract void printTransactionSummary();
}



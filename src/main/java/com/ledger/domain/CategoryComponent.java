package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "level", discriminatorType = DiscriminatorType.STRING)
public abstract class CategoryComponent {
    public enum CategoryType {
        INCOME, EXPENSE, TRANSFER, ROOT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    protected String name;

    @Column(length = 20, nullable = false)
    protected CategoryType type; //"income", "expense", "root"

    @OneToMany(mappedBy = "category", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    protected List<Transaction> transactions = new ArrayList<>(); //un category -> più transazioni.

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    protected List<Budget> budgets = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "parent_id")
    protected CategoryComponent parent;

    public CategoryComponent() {}
    public CategoryComponent(String name, CategoryType type) {
        this.name = name;
        this.type = type;
    }
    public abstract void remove(CategoryComponent child);
    public abstract void add(CategoryComponent child);
    public abstract List<CategoryComponent> getChildren();
    public abstract void display(String indent);
    public CategoryComponent getParent(){
        return this.parent;
    }
    public void setParent(CategoryComponent parent) {
        this.parent = parent;
    }
    public String getName() {
        return name;
    }
    public CategoryType getType() {
        return type;
    }
    public Long getId() {
        return id;
    }
    public void setName(String name) {
        this.name = name;
    }
    //public abstract void changeLevel(CategoryComponent root);

    public void addTransaction(Transaction t) {
        transactions.add(t);
    }
    public void addBudget(Budget b) {
        budgets.add(b);
    }


    public List<Transaction> getTransactionInPeriod(User user, Budget.BudgetPeriod period){
        return transactions.stream()
                .filter(t-> t.type==TransactionType.EXPENSE)
                .filter(t-> t.getLedger().getOwner().equals(user))
                .filter(t-> t.isInPeriod(period))
                //.filter(t-> Budget.isTransactionInPeriod(t, period))
                .toList();
    }

    //total spending for a user for a specific period
    public BigDecimal getTotalSpendingForPeriod(User user, Budget.BudgetPeriod period) {
        return getTransactionInPeriod(user,period).stream()
                .filter(t-> t.type==TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //returns a budget for the category/subcategory
    public Budget getBudgetForPeriod(Budget.BudgetPeriod p) {
        List<Budget> matchingBudgets = budgets.stream()
                .filter(b -> b.getPeriod() == p)
                .filter(b -> b.isInPeriod(LocalDate.now()))
                .toList();

        if (matchingBudgets.isEmpty()) {
            return null;
        }
        if (matchingBudgets.size() > 1) {
            throw new IllegalStateException("More than one budget found for the same period: " + p);
        }
        return matchingBudgets.get(0);
    }

    //returns total budget for the category and its subcategories in period
    public BigDecimal getTotalBudgetForPeriod(Budget.BudgetPeriod period) {
        return getBudgetForPeriod(period).getAmount();

    }

    public List<Transaction> getTransactions(){
        return transactions.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }
    public List<Transaction> getTransactionsForMonth(YearMonth month){
        return getTransactions().stream()
                .filter(t -> t.getDate().getYear() == month.getYear() && t.getDate().getMonth() == month.getMonth())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }
    public abstract void changeLevel(CategoryComponent root);
    public abstract void printTransactionSummary();
}



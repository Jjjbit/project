package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@DiscriminatorValue("Category")
public class Category extends CategoryComponent {

    @OneToMany(mappedBy = "parent", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private List<CategoryComponent> children = new ArrayList<>();

    public Category() {}
    public Category(String name, CategoryType type) {
        super(name, type);
    }

    public void changeLevel( CategoryComponent parent) {
        if (this instanceof Category && this.getChildren().isEmpty() && this.type != CategoryType.ROOT) {
            SubCategory sub = new SubCategory(this.name, this.type);
            sub.transactions.addAll(this.getTransactions()); // Copia le transazioni dalla categoria alla subcategoria
            parent.add(sub);
            this.parent.remove(this);
        }else {
            System.out.println("Cannot demote a category with subcategory.");
        }
    }

    @Override
    public void remove(CategoryComponent child) {
        children.remove(child);
    }

    @Override
    public void add(CategoryComponent child) {
        if (this.type == CategoryType.ROOT &&
                (child.type != CategoryType.ROOT)) {
            children.add(child);
            child.setParent(this);
        } else if (this.type != CategoryType.ROOT && child.type == this.type) {
            children.add(child);
            child.setParent(this);
        } else {
            throw new IllegalArgumentException("Invalid category hierarchy");
        }
    }

    @Override
    public List<CategoryComponent> getChildren() {
        return children;
    }

    // Ritorna la lista delle transazioni di questa categoria e delle sue subcategorie in ordine decrescente di data
    @Override
    public List<Transaction> getTransactions() {
        List<Transaction> all = new ArrayList<>(this.transactions);
        for (CategoryComponent child : children) {
            all.addAll(child.getTransactions());
        }
        return all.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }

    //ritorna una lista di budget di questa categoria e delle sue subcategorie
    public List<Budget> getBudgetsForPeriod(Budget.BudgetPeriod p) {
        List<Budget> result = new ArrayList<>();
        result.add(super.getBudgetForPeriod(p));

        children.forEach(sc -> result.add(sc.getBudgetForPeriod(p)));

        return result;
    }

    @Override
    public List<Transaction> getTransactionsForMonth(YearMonth month) {
       return getTransactions().stream()
                .filter(t -> t.getDate().getYear() == month.getYear() && t.getDate().getMonth() == month.getMonth())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public BigDecimal getTotalBudgetForPeriod(Budget.BudgetPeriod period) {
        BigDecimal ownBudget = super.getTotalBudgetForPeriod(period);

        BigDecimal subBudgets = children.stream()
                .map(sc -> sc.getTotalBudgetForPeriod(period))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ownBudget.add(subBudgets);
    }

    @Override
    public BigDecimal getTotalSpendingForPeriod(User user, Budget.BudgetPeriod period) {
        BigDecimal ownSpending = super.getTotalSpendingForPeriod(user, period);

        BigDecimal subSpending = children.stream()
                .map(sc -> sc.getTotalSpendingForPeriod(user, period))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ownSpending.add(subSpending);
    }


    //stampa un riepilogo delle transazioni della categoria in ordine decrescente di data
    @Override
    public void printTransactionSummary() {
        this.transactions = getTransactions();
        System.out.println("Transaction summary for: " + name);
        for (Transaction t : this.transactions) {
            System.out.println(t.getDate() + " - " + t.getAccount() + " - " + t.getAmount() + " - " + t.getNote());
        }
    }

    @Override
    public CategoryComponent getParent() {
        if(this.type==CategoryType.ROOT) {
            return null; // Le categorie di tipo ROOT non hanno un parent
        }else{
            return this.parent; //altrimenti
        }
    }
    @Override
    public void display(String indent) {
        System.out.println(indent + "- " + name + " (" + type + ")");
        for (CategoryComponent child : children) {
            child.display(indent + "  ");
        }
    }

}



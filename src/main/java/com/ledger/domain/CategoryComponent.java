package com.ledger.domain;

import java.util.ArrayList;
import java.util.List;

public abstract class CategoryComponent {
    protected String name;
    protected String type;
    protected List<Transaction> transactions = new ArrayList<>();

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

    public void addTransaction(Transaction t) {
        transactions.add(t);
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void printTransactionSummary() {
        System.out.println("Transaction summary for: " + name);
        for (Transaction t : transactions) {
            System.out.println(t.getDate() + " - " + t.getAccount() + "-" + t.getAmount() + " - " + t.getDescription());
        }
    }
}

package com.ledger.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@DiscriminatorValue("SubCategory")
public class SubCategory extends CategoryComponent {
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private CategoryComponent parent;

    public SubCategory() {}
    public SubCategory(String name, String type) {
        super(name, type);
    }
    @Override
    public void remove(CategoryComponent child) {
        throw new UnsupportedOperationException("SubCategory does not support remove operation");
    }
    @Override
    public void add(CategoryComponent child) {
        throw new UnsupportedOperationException("SubCategory does not support add operation");
    }
    @Override
    public List<CategoryComponent> getChildren() {
        throw new UnsupportedOperationException("SubCategory does not support getChildren operation");
    }
    @Override
    public CategoryComponent getParent() {
        return this.parent;
    }
    @Override
    public void display(String indent) {
        System.out.println(indent + "- " + name + " (" + type + ")");
    }
    @Override
    public List<Transaction> getTransactions() {
        return transactions.stream()
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }
    @Override
    public void printTransactionSummary() {
        this.transactions= getTransactions();
        System.out.println("Transaction summary for: " + name);
        for (Transaction t : transactions) {
            System.out.println(t.getDate() + " - " + t.getAccount() + "-" + t.getAmount() + " - " + t.getNote());
        }
    }

    public void changeLevel(CategoryComponent root) {
        if( this.getParent() != null) {
            this.getParent().remove(this);
            root.add(this);
        }

    }

    public void setParent(CategoryComponent parent) { //cambia o assegna il parent della SubCategory
        this.parent.remove(this); // Rimuove la SubCategory dal suo parent attuale
        this.parent = parent;
    }

}



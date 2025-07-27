package com.ledger.domain;

import java.util.ArrayList;
import java.util.List;

public class Category extends CategoryComponent {
    private List<CategoryComponent> children = new ArrayList<>();

    public Category(String name, String type) {
        super(name, type);
    }

    @Override
    public void remove(CategoryComponent child) {
        children.remove(child);
    }

    @Override
    public void add(CategoryComponent child) { // Aggiunge una SubCategory a Category
        if (child instanceof SubCategory) {
            ((SubCategory) child).setParent(this); // Imposta il parent della SubCategory
        }
        children.add(child); // Aggiunge una SubCategory a Category
    }

    @Override
    public List<CategoryComponent> getChildren() {
        return children;
    }

    public List<Transaction> getTransactions() {
        List<Transaction> all = new ArrayList<>(this.transactions);
        for (CategoryComponent child : children) {
            all.addAll(child.getTransactions());
        }
        return all;
    }

    @Override
    public void printTransactionSummary() {
        System.out.println("Transaction summary for: " + name);
        for (CategoryComponent sub : this.children) {
            sub.printTransactionSummary();
        }
    }

    public CategoryComponent getParent() {
        return null; // Le categorie non hanno un parent, ma le subcategorie sì
    }
    public void display(String indent) {
        System.out.println(indent + "- " + name + " (" + type + ")");
        for (CategoryComponent child : children) {
            child.display(indent + "  ");
        }
    }


}



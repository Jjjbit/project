package com.ledger.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

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
    public Category(String name, String type) {
        super(name, type);
    }

    public void changeLevel(CategoryComponent root, CategoryComponent parent) {
        if (this instanceof Category && this.getChildren().isEmpty()) {
            SubCategory sub = new SubCategory(this.name, this.type);
            sub.transactions.addAll(this.getTransactions()); // Copia le transazioni dalla categoria alla subcategoria
            parent.add(sub);
            root.remove(this);
        }else {
            System.out.println("Cannot demote a category with subcategory.");
        }
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

    //stampa un riepilogo delle transazioni della categoria in ordine decrescente di data
    @Override
    public void printTransactionSummary() {
        this.transactions = getTransactions();
        System.out.println("Transaction summary for: " + name);
        for (Transaction t : this.transactions) {
            System.out.println(t.getDate() + " - " + t.getAccount() + " - " + t.getAmount() + " - " + t.getNote());
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



package com.ledger.domain;

import java.util.ArrayList;
import java.util.List;

//ledger_categories
public class LedgerCategory {
    private Long id;
    private String name;
    private LedgerCategory parent;
    protected CategoryType type;
    protected Ledger ledger;
    private List<LedgerCategory> children = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private List<Budget> budgets = new ArrayList<>();

    public LedgerCategory() {}
    public LedgerCategory(String name, CategoryType type, Ledger ledger) {
        this.ledger = ledger;
        this.type = type;
        this.name = name;
    }



    // --- Getter/Setter ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Ledger getLedger() { return ledger; }
    public CategoryType getType() { return type; }
    public void setLedger(Ledger ledger) { this.ledger = ledger; }
    public void setType(CategoryType type) { this.type = type; }
    public void setBudgets(List<Budget> budgets) { this.budgets = budgets; }
    public LedgerCategory getParent() { return parent; }
    public void setParent(LedgerCategory parent) { this.parent = parent; }
    public List<Budget> getBudgets() { return budgets; }

    public List<LedgerCategory> getChildren() { return children; }
    public void setChildren(List<LedgerCategory> children) { this.children = children; }
    public List<Transaction> getTransactions() {
        return transactions;
    }
}



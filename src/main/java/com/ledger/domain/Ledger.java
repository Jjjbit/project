package com.ledger.domain;

public class Ledger {
    private long id;
    private String name;
    private User owner;
    //private List<Transaction> transactions=new ArrayList<>(); //relazione tra Transaction e Ledger è composizione
    //private List<LedgerCategory> categories = new ArrayList<>();
    //private List<Budget> budgets = new ArrayList<>();

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName(){return this.name;}
    public void setName(String name){this.name=name;}
    public User getOwner(){return this.owner;}
    public void setOwner(User owner){this.owner=owner;}
    //public List<Transaction> getTransactions() { return transactions;}
    //public void setTransactions(List<Transaction> transactions) { this.transactions = transactions;}
    //public List<LedgerCategory> getCategories(){return categories;}
    //public void setCategories(List<LedgerCategory> categories){this.categories=categories;}
    //public List<Budget> getBudgets(){return budgets;}
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
}



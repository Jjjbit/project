package com.ledger.domain;

public class Ledger {
    private long id;
    private String name;
    private User owner;

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName(){return this.name;}
    public void setName(String name){this.name=name;}
    public User getOwner(){return this.owner;}
    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
}



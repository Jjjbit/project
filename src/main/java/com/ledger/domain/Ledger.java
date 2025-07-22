package com.ledger.domain;
import java.util.List;

public class Ledger {
    private String name;
    private User owner;
    private List<Transaction> transactions;

    public Ledger(String name, User owner, List<Transaction> transactions) {
        this.name = name;
        this.owner = owner;
        this.transactions = transactions;
    }
}

package com.ledger.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Ledger {
    @Id
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions=new ArrayList<>(); //relazione tra Transaction e Ledger è aggregazione

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BorrowingAndLending> loanRecords = new ArrayList<>(); // relazione tra LoanRecord e Ledger è aggregazione

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }
    public void addTransaction(Transaction tx) {
        transactions.add(tx); // Aggiunge una transazione al ledger
        tx.execute();
    }
    public void removeTransaction(Transaction tx) {
        transactions.remove(tx); // Rimuove una transazione dal ledger
    }
    public void addLoanRecord(BorrowingAndLending loanRecord) {
        loanRecords.add(loanRecord);
    }

}



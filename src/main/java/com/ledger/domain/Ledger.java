package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "ledgers")
public class Ledger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length =50, nullable= false, unique = true)
    private String name;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions=new ArrayList<>(); //relazione tra Transaction e Ledger è composizione

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LedgerCategory> categories = new ArrayList<>();

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    public String getName(){return this.name;}
    public void setName(String name){this.name=name;}
    public User getOwner(){return this.owner;}
    public void setOwner(User owner){this.owner=owner;}
    public List<Transaction> getTransactions() {
        return transactions;
    }
    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }
    public List<LedgerCategory> getCategories(){return categories;}
    public void setCategories(List<LedgerCategory> categories){this.categories=categories;}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }


    //does not matter income or expense
    public List<Transaction> getTransactionsForMonth(YearMonth month) {
        return transactions.stream()
                .filter(t -> t.getDate().getYear() == month.getYear() && t.getDate().getMonth() == month.getMonth())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .toList();
    }

    public BigDecimal getTotalIncomeForMonth(YearMonth month) { //used by test
        return getTransactionsForMonth(month).stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

    }
    public BigDecimal getTotalExpenseForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx ->tx.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}



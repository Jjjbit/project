package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
public class Ledger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false, unique = true)
    private String name;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions=new ArrayList<>(); //relazione tra Transaction e Ledger è aggregazione

    @OneToMany(mappedBy = "ledger", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BorrowingAndLending> loanRecords = new ArrayList<>(); // relazione tra LoanRecord e Ledger è aggregazione

    @Column(name = "total_income", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalIncome = BigDecimal.ZERO; // Totale entrate del ledger

    @Column(name = "total_expenses", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalExpenses = BigDecimal.ZERO; // Totale uscite del ledger

    public Ledger() {}
    public Ledger(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }
    public void addTransaction(Transaction tx) {
        transactions.add(tx); // Aggiunge una transazione al ledger
        tx.execute();
        if(tx.getType() == TransactionType.INCOME) {
            totalIncome = totalIncome.add(tx.getAmount()); // Aggiorna il totale delle entrate
        } else if(tx.getType() == TransactionType.EXPENSE) {
            totalExpenses = totalExpenses.add(tx.getAmount()); // Aggiorna il totale delle uscite
        }
    }
    public void removeTransaction(Transaction tx) {
        transactions.remove(tx); // Rimuove una transazione dal ledger
        if(tx.getType() == TransactionType.INCOME) {
            totalIncome = totalIncome.subtract(tx.getAmount()); // Aggiorna il totale delle entrate
        } else if(tx.getType() == TransactionType.EXPENSE) {
            totalExpenses = totalExpenses.subtract(tx.getAmount()); // Aggiorna il totale delle uscite
        }
    }
    public void addLoanRecord(BorrowingAndLending loanRecord) {
        loanRecords.add(loanRecord);
    }
    public List<Transaction> getTransactionsForMonth(YearMonth month) {
        return transactions.stream()
                .filter(tx -> YearMonth.from(tx.getDate()).equals(month))
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .toList();
    }
    public String getName() {
        return name;
    }
    public User getOwner() {
        return owner;
    }

}



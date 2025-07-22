package com.ledger.domain;

public class Transaction {
    //private LocalDate date;
    private double amount;
    private String description;
    private TransactionType type;
    private Account sourceAccount;
    private Account destinationAccount; //only for TRANSFER
    Category category;
    SubCategory subCategory;
}

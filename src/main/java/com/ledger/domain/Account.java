package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Account {
    protected Long id;
    protected String name;
    protected BigDecimal balance = BigDecimal.ZERO;
    protected AccountType type;
    protected AccountCategory category;
    protected User owner;
    protected String notes;
    protected Boolean hidden=false;
    protected List<Transaction> outgoingTransactions = new ArrayList<>();
    protected List<Transaction> incomingTransactions = new ArrayList<>();
    protected Boolean includedInNetAsset = true;
    protected Boolean selectable = true;

    public Account() {}
    public Account(
            String name,
            BigDecimal balance,
            AccountType type,
            AccountCategory category,
            User owner,
            String notes,
            boolean includedInNetAsset,
            boolean selectable) {
        this.name = name;
        this.balance = balance != null ? balance : BigDecimal.ZERO;
        this.type = type;
        this.category = category;
        this.owner = owner;
        this.notes = notes;
        this.includedInNetAsset = includedInNetAsset;
        this.selectable = selectable;
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount).setScale(2, RoundingMode.HALF_UP);
    }
    public abstract void debit(BigDecimal amount);
    public void hide() {
        this.hidden = true;
    }

    public void setIncludedInNetAsset(boolean includedInNetAsset) {
        this.includedInNetAsset =includedInNetAsset;
    }
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }
    public void setOwner(User owner) {
        this.owner = owner;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public void setBalance(BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative.");
        }
        this.balance = balance;
    }

    public AccountType getType() { return type; }
    public AccountCategory getCategory() {
        return category;
    }
    public String getName() {
        return name;
    }
    public User getOwner() {
        return owner;
    }
    public List<Transaction> getTransactions() {
        List<Transaction> allTransactions = new ArrayList<>();
        allTransactions.addAll(incomingTransactions);
        allTransactions.addAll(outgoingTransactions);
        return allTransactions;
    }
    public List<Transaction> getIncomingTransactions() {
        return incomingTransactions;
    }
    public List<Transaction> getOutgoingTransactions() {
        return outgoingTransactions;
    }
    public BigDecimal getBalance() {
        return this.balance;
    }
    public Long getId() {
        return id;
    }
    public String getNotes() {
        return notes;
    }
    public Boolean getSelectable() {
        return selectable;
    }
    public Boolean getHidden() {
        return hidden;
    }
    public Boolean getIncludedInNetAsset() {
        return includedInNetAsset;
    }
    public void setCategory(AccountCategory category) {
        this.category = category;
    }
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    public void setType(AccountType type) {
        this.type = type;
    }
    public void setIncomingTransactions(List<Transaction> incomingTransactions) {
        this.incomingTransactions = incomingTransactions;
    }
    public void setOutgoingTransactions(List<Transaction> outgoingTransactions) {
        this.outgoingTransactions = outgoingTransactions;
    }


    public void addTransaction(Transaction transaction) { //for test
        if(transaction instanceof Income){
            if ( !this.hidden && this.selectable) {
                credit(transaction.getAmount());
                incomingTransactions.add(transaction);
            }
        } else if (transaction instanceof Expense) {
            if (!this.hidden && this.selectable) {
                if (! this.category.equals(AccountCategory.CREDIT) && this.balance.compareTo(transaction.getAmount()) <= 0) {
                    throw new IllegalArgumentException("Insufficient funds in the account to execute this transaction.");
                }
                debit(transaction.getAmount());
                outgoingTransactions.add(transaction);
            }

        } else if (transaction instanceof Transfer) {
            if ((transaction.getFromAccount() != null && transaction.getFromAccount().equals(this))) {
                if (! this.category.equals(AccountCategory.CREDIT) && this.balance.compareTo(transaction.getAmount()) <= 0) {
                    throw new IllegalArgumentException("Insufficient funds in the account to execute this transaction.");
                }
                debit(transaction.getAmount());
                outgoingTransactions.add(transaction);
            }
            if (transaction.getToAccount() != null &&  transaction.getToAccount().equals(this)) {
                credit(transaction.getAmount());
                incomingTransactions.add(transaction);
            }
        }
    }


    public List<Transaction> getTransactionsForMonth(YearMonth month) {
        return getTransactions().stream()
                .filter(t -> t.getDate().getYear() == month.getYear() && t.getDate().getMonth() == month.getMonth())
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());
    }

    public BigDecimal getAccountTotalIncomeForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx -> tx.getType() == TransactionType.INCOME
                        || (tx.getType() == TransactionType.TRANSFER
                        && tx instanceof Transfer
                        && ((Transfer) tx).getToAccount().equals(this)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal getAccountTotalExpenseForMonth(YearMonth month) {
        return getTransactionsForMonth(month).stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE
                        || (tx.getType() == TransactionType.TRANSFER
                        && tx instanceof Transfer
                        && ((Transfer) tx).getFromAccount().equals(this)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}

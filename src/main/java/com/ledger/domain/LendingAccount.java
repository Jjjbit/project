package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class LendingAccount extends Account {
    private boolean isEnded = false; //indica se il borrowing è stato completamente rimborsato
    private LocalDate date;

    public LendingAccount() {}
    public LendingAccount(String name,  //person or entity from whom the money is lent
                          BigDecimal balance, //bilancio da pagare da utente
                          String note,
                          boolean includedInNetWorth,
                          boolean selectable,
                          User owner,
                          LocalDate date) {
        super(name, balance, AccountType.LENDING, AccountCategory.VIRTUAL_ACCOUNT, owner, note, includedInNetWorth, selectable);
        this.date = date;
    }

    public void setIsEnded(boolean ended) {
        isEnded = ended;
    }
    public void setDate(LocalDate date) {
        this.date = date;
    }
    public boolean getIsEnded() {
        return isEnded;
    }
    public LocalDate getDate() {
        return date;
    }
    public BigDecimal getLendingAmount() {
        return balance;
    }
    public void receiveRepayment(Transaction tx, BigDecimal amount){
        balance = balance.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        transactions.add(tx);
        //outgoingTransactions.add(tx);
        checkAndUpdateStatus();
    }

    public void checkAndUpdateStatus() {
        if(balance.compareTo(BigDecimal.ZERO) <= 0) {
            this.isEnded = true;
        } else {
            this.isEnded = false;
        }
    }
}

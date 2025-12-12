package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class BorrowingAccount extends Account{
    private boolean isEnded; //indica se il borrowing è stato completamente rimborsato
    private LocalDate date;
    private BigDecimal borrowingAmount;
    private BigDecimal remainingAmount;

    public BorrowingAccount() {}
    public BorrowingAccount(String name,  //person or entity from whom the money is borrowed
                            BigDecimal borrowingAmount, //bilancio da pagare da utente
                            String note,
                            boolean includedInNetWorth,
                            boolean selectable,
                            User owner,
                            LocalDate date) {
        super(name, BigDecimal.ZERO, AccountType.BORROWING, AccountCategory.VIRTUAL_ACCOUNT, owner, note, includedInNetWorth, selectable);
        this.date=date;
        this.borrowingAmount=borrowingAmount;
        this.remainingAmount=borrowingAmount;
        this.isEnded = false;
    }

    public void setBorrowingDate(LocalDate date){this.date=date;}
    public boolean getIsEnded() {
        return isEnded;
    }
    public void setIsEnded(boolean isEnded) {
        this.isEnded = isEnded;
    }
    public LocalDate getBorrowingDate() {
        return date;
    }
    public BigDecimal getBorrowingAmount(){return borrowingAmount;}
    public void setBorrowingAmount(BigDecimal borrowingAmount) {
        this.borrowingAmount=borrowingAmount;
    }
    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }
    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    @Override
    public void credit(BigDecimal amount) {
        this.remainingAmount = this.remainingAmount.subtract(amount).setScale(2, RoundingMode.HALF_UP);
        checkAndUpdateStatus();
    }

    @Override
    public void debit(BigDecimal amount){
        this.remainingAmount = this.remainingAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
        this.borrowingAmount = this.borrowingAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public void repay(BigDecimal amount){
        credit(amount);
        checkAndUpdateStatus();
    }

    public void checkAndUpdateStatus() {
        if(remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            this.remainingAmount=BigDecimal.ZERO;
            this.isEnded = true;
        } else {
            this.isEnded = false;
        }
    }
}
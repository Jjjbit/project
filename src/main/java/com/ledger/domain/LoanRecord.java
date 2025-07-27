package com.ledger.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public abstract class LoanRecord {
    protected String name;
    protected BigDecimal totalAmount;
    protected BigDecimal repaidAmount= BigDecimal.ZERO;
    protected LocalDate loanDate= LocalDate.now();
    protected String notes; //può essere null
    protected Ledger ledger;
    protected Currency currency;
    protected boolean includedInNetWorth = true;
    protected boolean isEnded = false;

    public LoanRecord(String name, BigDecimal amount, String notes, Ledger ledger, Currency currency) {
        this.name = name;
        this.totalAmount = amount;
        this.notes = notes;
        this.ledger = ledger;
        this.currency = currency;

    }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getRepaidAmount() { return repaidAmount; }
    public boolean isFullyRepaid() {
        return repaidAmount.compareTo(totalAmount) >= 0;
    }
    public BigDecimal getRemaining() {
        return totalAmount.subtract(repaidAmount);
    }
    public abstract boolean isIncoming();
    public void setName(String name) {
        this.name = name;
    }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    public void setRepaidAmount(BigDecimal repaidAmount) {
        this.repaidAmount = repaidAmount;
        checkAndUpdateStatus();
    }
    public void setLoanDate(LocalDate loanDate) {
        this.loanDate = loanDate;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }
    public void setIncludedInNetWorth(boolean includedInNetWorth) {
        this.includedInNetWorth = includedInNetWorth;
    }
    public void setloanDate(LocalDate loanDate) {
        this.loanDate = loanDate;
    }


    public void endLoan() {
        isEnded = true;
    }
    private void checkAndUpdateStatus() {
        if (repaidAmount.compareTo(totalAmount) >= 0) {
            isEnded = true;
        }
    }




}

package com.ledger.domain;

import java.math.BigDecimal;

public class Reimbursement {
    private long id;
    private BigDecimal amount;
    private BigDecimal remainingAmount;
    private boolean isEnded;
    private Ledger ledger;
    private Account fromAccount;
    private LedgerCategory ledgerCategory;

    public Reimbursement(
            BigDecimal amount,
            boolean isEnded,
            Account fromAccount,
            Ledger ledger, LedgerCategory ledgerCategory
    ) {
        this.fromAccount = fromAccount;
        this.ledger = ledger;
        this.amount = amount;
        this.isEnded = isEnded;
        this.remainingAmount = amount;
        this.ledgerCategory = ledgerCategory;
    }
    public Reimbursement() {}

    public LedgerCategory getLedgerCategory() {
        return ledgerCategory;
    }
    public void setLedgerCategory(LedgerCategory ledgerCategory) {
        this.ledgerCategory = ledgerCategory;
    }
    public Account getFromAccount() {
        return fromAccount;
    }
    public void setFromAccount(Account fromAccount) {
        this.fromAccount = fromAccount;
    }
    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }
    public Ledger getLedger() {
        return ledger;
    }
    public void setLedger(Ledger ledger) {
        this.ledger = ledger;
    }

    public boolean isEnded() {
        return isEnded;
    }
    public void setEnded(boolean ended) {
        isEnded = ended;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

}

package com.ledger.domain;

import java.math.BigDecimal;

public class Reimbursement {
    private long id;
    private Transaction originalTransaction;
    private BigDecimal amount;
    private BigDecimal remainingAmount;
    private ReimbursableStatus status;
    private Ledger ledger;

    public Reimbursement(Transaction originalTransaction,
                         BigDecimal amount, ReimbursableStatus status, Ledger ledger) {
        this.ledger = ledger;
        this.originalTransaction = originalTransaction;
        this.amount = amount;
        this.status = status;
        this.remainingAmount = amount;
    }
    public Reimbursement() {}

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

    public ReimbursableStatus getReimbursementStatus() {
        return status;
    }
    public void setStatus(ReimbursableStatus status) {
        this.status = status;
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
    public Transaction getOriginalTransaction() {
        return originalTransaction;
    }
    public void setOriginalTransaction(Transaction originalTransaction) {
        this.originalTransaction = originalTransaction;
    }

}

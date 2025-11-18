package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CreditAccount extends Account {
    private BigDecimal creditLimit;
    private BigDecimal currentDebt;
    private Integer billDay;
    private Integer dueDay;
    //private List<Installment> installments;

    public CreditAccount(){}
    public CreditAccount(String name,
                         BigDecimal balance,
                         User owner,
                         String notes,
                         boolean includedInNetWorth,
                         boolean selectable,
                         BigDecimal creditLimit,
                         BigDecimal currentDebt, //contiene importo residuo delle rate
                         Integer billDate,
                         Integer dueDate,
                         AccountType type) {
        super(name, balance, type, AccountCategory.CREDIT, owner, notes, includedInNetWorth, selectable);
        this.creditLimit = creditLimit;
        if(currentDebt == null){
            this.currentDebt=BigDecimal.ZERO;
        }else {
            this.currentDebt = currentDebt;
        }
        this.billDay = billDate;
        this.dueDay = dueDate;
        //this.installments = new ArrayList<>();
    }

    public BigDecimal getCurrentDebt() {
        return currentDebt;
    }
    public BigDecimal getCreditLimit(){return creditLimit;}
    public Integer getBillDay(){return billDay;}
    public Integer getDueDay(){return dueDay;}
    public void setCurrentDebt(BigDecimal currentDebt) {
        this.currentDebt = currentDebt;
    }
    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }
    public void setBillDay(Integer billDate) {
        this.billDay = billDate;
    }
    public void setDueDay(Integer dueDate) {
        this.dueDay = dueDate;
    }


    public void repayDebt(Transaction tx){
        //transactions.add(tx);
        currentDebt = currentDebt.subtract(tx.getAmount()).setScale(2, RoundingMode.HALF_UP);
        if (currentDebt.compareTo(BigDecimal.ZERO) < 0) {
            currentDebt = BigDecimal.ZERO;
        }
    }

    //public List<Installment> getInstallmentPlans() {return installments;}
}



package com.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("CreditAccount")
public class CreditAccount extends Account {

    @Column(name = "credit_limit", precision = 15, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    @Column(name = "current_debt", precision = 15, scale = 2, nullable = false)
    private BigDecimal currentDebt;

    @Column(name = "bill_date")
    private MonthDay billDate;

    @Column(name = "due_date")
    private MonthDay dueDate;

    @OneToMany(mappedBy = "linkedAccount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InstallmentPlan> installmentPlans;

    public CreditAccount(){}
    public CreditAccount(String name,
                         BigDecimal balance,
                         User owner,
                         String notes,
                         Currency currency,
                         boolean includedInNetWorth,
                         boolean selectable,
                         BigDecimal creditLimit,
                         BigDecimal currentDebt,
                         MonthDay billDate,
                         MonthDay dueDate, AccountType type) {
        super(name, balance, type, AccountCategory.CREDIT, owner, currency, notes, includedInNetWorth, selectable);
        this.creditLimit = creditLimit;
        this.currentDebt = currentDebt;
        this.billDate = billDate;
        this.dueDate = dueDate;
        this.installmentPlans = new ArrayList <>();
    }

    public BigDecimal getCurrentDebt() {
        return currentDebt;
    }

    @Override
    public void debit(BigDecimal amount) {
        if (amount.compareTo(balance) > 0) {
            if (currentDebt.add(amount.subtract(balance)).compareTo(creditLimit) > 0) {
                throw new IllegalArgumentException("Amount exceeds credit limit");
            }else{
                balance = BigDecimal.ZERO;
                currentDebt = currentDebt.add(amount.subtract(balance));
            }
        }
        currentDebt = currentDebt.add(amount);
        this.owner.updateNetAssetsAndLiabilities(amount.negate());
    }
    public void addNewDebt(BigDecimal amount) {
        currentDebt = currentDebt.add(amount);
        this.owner.updateNetAssetsAndLiabilities(amount);
    }

    public void repayDebt(BigDecimal amount, Account fromAccount) {
        currentDebt = currentDebt.subtract(amount);
        if(fromAccount != null) {
            fromAccount.debit(amount);
        }else {
            this.owner.updateNetAssetsAndLiabilities(amount);
        }
    }

    public List<InstallmentPlan> getInstallmentPlans() {
        return installmentPlans;
    }
    public void addInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlans.add(installmentPlan);
        //currentDebt = currentDebt.add(installmentPlan.getRemainingAmount());
    }
    public BigDecimal getRemainingInstallmentDebt(){
        return installmentPlans.stream()
                .map(InstallmentPlan::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}



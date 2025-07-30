package com.ledger.domain;

import java.math.BigDecimal;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;

public class CreditAccount extends Account {
    //private CreditCardMetadataStrategy creditCardDetails;
    private BigDecimal creditLimit;
    private BigDecimal currentDebt;
    private MonthDay billDate;
    private MonthDay dueDate;
    private List<InstallmentPlan> installmentPlans;

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
        //this.creditCardDetails = creditCardDetails;
        this.creditLimit = creditLimit;
        this.currentDebt = currentDebt;
        this.billDate = billDate;
        this.dueDate = dueDate;
        this.installmentPlans = new ArrayList <>();
    }

    public BigDecimal getCurrentDebt() {
        return currentDebt;
    }
    public void repayDebt(BigDecimal amount, Account fromAccount) {
        currentDebt = currentDebt.subtract(amount);
        if(fromAccount != null) {
            fromAccount.debit(amount);
        }else {
            this.owner.updateNetAssetsAndLiabilities(amount);
        }
    }

    /*public Optional<InstallmentPlan> getInstallmentPlan() {
        return Optional.ofNullable(installmentPlan);
    }*/
    public List<InstallmentPlan> getInstallmentPlans() {
        return installmentPlans;
    }
    public void addInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlans.add(installmentPlan);
        currentDebt = currentDebt.add(installmentPlan.getRemainingAmount());
    }
    public BigDecimal getRemainingInstallmentDebt(){
        return installmentPlans.stream()
                .map(InstallmentPlan::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    /*public void createInstallment(BigDecimal amount, int periods, BigDecimal feeRate, int paidPeriods, InstallmentPlan.FeeStrategy feeStrategy){
        this.installmentPlan = new InstallmentPlan(amount, periods, feeRate, paidPeriods, feeStrategy, this);
        this.currentDebt = currentDebt.add(amount);
    }*/

    /*public BigDecimal getRemainingInstallmentDebt() {
        return installmentPlan != null ? installmentPlan.getRemainingAmount() : BigDecimal.ZERO;
    }*/


    /*public CreditCardMetadataStrategy getCreditCardDetails() {
        return creditCardDetails;
    }*/


    //ritorna i campi che eistono solo nelle credit card
    /*@Override
    public Map<String, Object> getMetadata() {
        return creditCardDetails.getFields();
    }*/
}



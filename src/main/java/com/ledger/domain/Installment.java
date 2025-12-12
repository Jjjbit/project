package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class Installment {

    public enum Strategy {
        EVENLY_SPLIT,
        UPFRONT,
        FINAL
    }

    private long id; // Unique identifier for the installment plan
    private BigDecimal totalAmount;
    private int totalPeriods;
    private BigDecimal interest;
    private int paidPeriods;
    private Strategy strategy;
    private Account linkedAccount; //composition
    private BigDecimal remainingAmount;
    private LocalDate repaymentStartDate;
    private LedgerCategory category; //association
    private String name;
    private boolean includedInCurrentDebts; // Whether this installment is included in current debts of linked account

    public Installment() {}
    public Installment(String name,
                       BigDecimal totalAmount,
                       int totalPeriods,
                       BigDecimal interest,
                       int paidPeriods,
                       Strategy strategy,
                       Account linkedAccount,
                       LocalDate repaymentStartDate,
                       LedgerCategory category,
                       boolean includedInCurrentDebts) {
        this.includedInCurrentDebts = includedInCurrentDebts;
        this.repaymentStartDate = repaymentStartDate;
        this.name = name;
        this.totalAmount = totalAmount;
        this.totalPeriods = totalPeriods;
        this.interest = interest;
        this.paidPeriods = paidPeriods;
        this.strategy = strategy;
        this.linkedAccount = linkedAccount;
        this.category = category;
        this.remainingAmount = getRemainingAmountWithRepaidPeriods();
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }
    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
    public Account getLinkedAccount() {
        return linkedAccount;
    }
    public long getId() {
        return id;
    }
    public void setIncludedInCurrentDebts(boolean includedInCurrentDebts) {
        this.includedInCurrentDebts = includedInCurrentDebts;
    }
    public boolean isIncludedInCurrentDebts() {
        return includedInCurrentDebts;
    }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    public void setTotalPeriods(int totalPeriods) {
        this.totalPeriods = totalPeriods;
    }
    public int getPaidPeriods() {
        return paidPeriods;
    }
    public void setPaidPeriods(int paidPeriods) {
        this.paidPeriods = paidPeriods;
    }
    public void setInterest(BigDecimal interest) {
        this.interest = interest;
    }
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    public int getTotalPeriods() {
        return totalPeriods;
    }
    public BigDecimal getInterest() {
        return interest;
    }
    public Strategy getStrategy() {
        return strategy;
    }
    public LocalDate getRepaymentStartDate() {
        return repaymentStartDate;
    }
    public String getName() {
        return name;
    }
    public void setId(long id) {
        this.id = id;
    }
    public void setRepaymentStartDate(LocalDate repaymentStartDate) {
        this.repaymentStartDate = repaymentStartDate;
    }
    public void setCategory(LedgerCategory category) {
        this.category = category;
    }
    public LedgerCategory getCategory() {
        return category;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setLinkedAccount(Account linkedAccount) {
        this.linkedAccount = linkedAccount;
    }

    public BigDecimal getMonthlyPayment(int period) {
        BigDecimal base = totalAmount.divide(BigDecimal.valueOf(totalPeriods), 10, RoundingMode.HALF_UP); //base amount per period
        BigDecimal totalInterest = totalAmount.multiply(interest.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP); //total roundedTotalInterest for the installment
        BigDecimal totalPayment = totalAmount.add(totalInterest);

        BigDecimal roundedBase = base.setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundedTotalInterest = totalInterest.setScale(2, RoundingMode.HALF_UP);

        switch(this.strategy){
            case EVENLY_SPLIT:
                if (period < totalPeriods) {
                    // regular monthly rounded
                    return totalPayment.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP);
                } else {
                    // last period absorbs difference
                    BigDecimal sum = BigDecimal.ZERO;
                    for (int i = 1; i < totalPeriods; i++) {
                        sum = sum.add(totalPayment.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP));
                    }
                    if(sum.compareTo(totalPayment) >= 0){
                        return BigDecimal.ZERO;
                    } else {
                        return totalPayment.subtract(sum).setScale(2, RoundingMode.HALF_UP);
                    }
                }
            case UPFRONT:
                if (period == 1) {
                    return roundedBase.add(roundedTotalInterest);
                } else if (period < totalPeriods) {
                    return roundedBase;
                } else {
                    // last one absorbs rounding diff
                    BigDecimal sum = BigDecimal.ZERO;
                    for (int i = 1; i < totalPeriods; i++) {
                        sum = sum.add(getMonthlyPayment(i));
                    }
                    return totalPayment.subtract(sum).setScale(2, RoundingMode.HALF_UP);
                }
            case FINAL:
                if(period < totalPeriods){
                    return roundedBase;
                }else {
                    // last one absorbs rounding diff
                    BigDecimal sum = BigDecimal.ZERO;
                    for (int i = 1; i < totalPeriods; i++) {
                        sum = sum.add(getMonthlyPayment(i));
                    }
                    return totalPayment.subtract(sum).setScale(2, RoundingMode.HALF_UP);
                }
            default:
                throw new IllegalArgumentException("Unknown roundedTotalInterest strategy: " + strategy); // For other strategies
        }

    }
    public BigDecimal getTotalPayment(){
        BigDecimal fee = totalAmount.multiply(interest.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP); //total fee for the installment
        return totalAmount.add(fee).setScale(2, RoundingMode.HALF_UP); //total amount + total interest
    }

    public void repayOnePeriod() {
        BigDecimal monthlyPayment = getMonthlyPayment(paidPeriods + 1);
        remainingAmount = remainingAmount.subtract(monthlyPayment).setScale(2, RoundingMode.HALF_UP);
        paidPeriods++;
    }
    public BigDecimal getRemainingAmountWithRepaidPeriods() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = paidPeriods + 1; i <= totalPeriods; i++) {
            total = total.add(getMonthlyPayment(i));
        }
        return total;
    }
}



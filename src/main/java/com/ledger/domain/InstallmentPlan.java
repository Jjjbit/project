package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InstallmentPlan {

    public enum FeeStrategy {
        EVENLY_SPLIT,
        UPFRONT,
        FINAL
    }

    private BigDecimal totalAmount;
    private int totalPeriods;
    private BigDecimal feeRate;
    private int paidPeriods = 0;
    private FeeStrategy feeStrategy = FeeStrategy.EVENLY_SPLIT;
    private Account linkedAccount;

    public InstallmentPlan(BigDecimal totalAmount, int totalPeriods, BigDecimal feeRate, int paidPeriods, FeeStrategy feeStrategy, Account linkedAccount) {
        this.totalAmount = totalAmount;
        this.totalPeriods = totalPeriods;
        this.feeRate = feeRate;
        this.paidPeriods = paidPeriods;
        this.feeStrategy = feeStrategy;
        this.linkedAccount = linkedAccount;
    }

    public BigDecimal getMonthlyPayment(int period) {
        BigDecimal base = totalAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //base amount per period
        BigDecimal fee = totalAmount.multiply(feeRate); //total fee for the installment

        switch(this.feeStrategy){
            case EVENLY_SPLIT:
                return (totalAmount.add(fee)).divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //(totalAmount+fee)/totalPeriods
            case UPFRONT:
                if(period == 0) {
                    return base.add(fee).setScale(2, RoundingMode.HALF_UP); //first payment includes all fees
                } else {
                    return base; //subsequent payments are just the base amount
                }
            case FINAL:
                if (period == totalPeriods){
                    return base.add(fee).setScale(2, RoundingMode.HALF_UP); //last payment includes all fees
                } else {
                    return base; //all other payments are just the base amount
                }
            default:
                throw new IllegalArgumentException("Unknown fee strategy: " + feeStrategy); // For other fee strategies
        }

    }
    public BigDecimal getTotalPayment(){
        BigDecimal fee= totalAmount.multiply(feeRate); //total fee for the installment
        return totalAmount.add(fee).setScale(2, RoundingMode.HALF_UP); //total amount + total fee
    }

    public void repayOnePeriod() {
        if (paidPeriods < totalPeriods) {
            BigDecimal amountToPay = getMonthlyPayment(paidPeriods + 1);
            linkedAccount.debit(amountToPay); // Debit the amount from the linked account
            paidPeriods++;
        } else {
            throw new IllegalStateException("All periods have already been paid.");
        }
    }
    public BigDecimal getRemainingAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = paidPeriods + 1; i <= totalPeriods; i++) {
            total = total.add(getMonthlyPayment(i));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }


    public boolean isCompleted() {
        return paidPeriods >= totalPeriods;
    }
}

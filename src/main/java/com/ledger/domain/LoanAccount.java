package com.ledger.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class LoanAccount extends Account {

    public enum RepaymentType {
        EQUAL_INTEREST,
        EQUAL_PRINCIPAL,
        EQUAL_PRINCIPAL_AND_INTEREST,
        INTEREST_BEFORE_PRINCIPAL
    }

    private int totalPeriods;
    private int repaidPeriods;
    private BigDecimal annualInterestRate;
    private BigDecimal loanAmount;
    private LocalDate repaymentDay;
    private RepaymentType repaymentType;
    private BigDecimal remainingAmount;
    private boolean isEnded;

    public LoanAccount() {}
    public LoanAccount(
            String name,
            User owner,
            String note,
            boolean includedInNetWorth,
            int totalPeriods,
            int repaidPeriods,
            BigDecimal interestRate,
            BigDecimal loanAmount,
            LocalDate repaymentDate,
            RepaymentType repaymentType) {
        super(name, BigDecimal.ZERO, AccountType.LOAN, AccountCategory.CREDIT, owner, note, includedInNetWorth, false);
        this.totalPeriods = totalPeriods;
        this.repaidPeriods = repaidPeriods;
        this.annualInterestRate = interestRate;
        this.loanAmount = loanAmount;
        this.repaymentDay = repaymentDate;
        this.repaymentType = repaymentType;
        this.remainingAmount= calculateRemainingAmount();
        this.isEnded = false;
    }

    public void setTotalPeriods(int totalPeriods) {
        this.totalPeriods = totalPeriods;
    }
    public void setRepaidPeriods(int repaidPeriods) {
        this.repaidPeriods = repaidPeriods;
    }
    public void setLoanAmount(BigDecimal loanAmount) {
        this.loanAmount = loanAmount;
    }
    public void setRepaymentDate(LocalDate repaymentDate) {
        this.repaymentDay = repaymentDate;
    }
    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
    public void setEnded(boolean ended) {
        isEnded = ended;
    }
    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }
    public void setRepaymentType(RepaymentType repaymentType) {
        this.repaymentType = repaymentType;
    }
    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }
    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }
    public BigDecimal getLoanAmount() {
        return loanAmount;
    }
    public LocalDate getRepaymentDay() {
        return repaymentDay;
    }
    public RepaymentType getRepaymentType() {
        return repaymentType;
    }
    public boolean getIsEnded() {
        return isEnded;
    }


    @Override
    public void debit(BigDecimal amount) {
        this.remainingAmount = this.remainingAmount.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public void credit(BigDecimal amount) {
        this.remainingAmount = this.remainingAmount.subtract(amount).setScale(2, RoundingMode.HALF_UP);
    }

    public int getTotalPeriods(){return this.totalPeriods;}
    public int getRepaidPeriods(){return this.repaidPeriods;}

    public BigDecimal getMonthlyRate() {
        if (annualInterestRate == null) return BigDecimal.ZERO;
        return annualInterestRate
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP) //convert percentage to decimal
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP); // convert annual rate to monthly rate
    }

    public void repayLoan(){ //pay one period
        this.repaidPeriods = this.repaidPeriods + 1;
        this.remainingAmount= remainingAmount.subtract(getMonthlyRepayment(repaidPeriods)).setScale(2, RoundingMode.HALF_UP);
        checkAndUpdateStatus();
    }

    public BigDecimal calculateRemainingAmount() { //dipende da repaidPeriods
        if (loanAmount == null || totalPeriods == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int i = repaidPeriods + 1; i <= totalPeriods; i++) {
            total = total.add(getMonthlyRepayment(i));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    //monthly rate r: annualInterestRate / 12
    // total periods n: totalPeriods
    // loan amount P: loanAmount
    public BigDecimal getMonthlyRepayment(int period){
        BigDecimal monthlyRate = getMonthlyRate();
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return loanAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP);
        }

        switch(this.repaymentType) {
            case EQUAL_INTEREST:
                // For EQUAL_INTEREST, the monthly repayment is calculated as follows:
                // M = (P * r * (1 + r)^n) / ((1 + r)^n - 1) = monthly payment is constant
                // where: M = monthly payment, P = loan amount, r = monthly rate, n = total periods
                BigDecimal numerator = loanAmount.multiply(monthlyRate).multiply(
                        (BigDecimal.ONE.add(monthlyRate)).pow(totalPeriods)
                );
                BigDecimal denominator = (BigDecimal.ONE.add(monthlyRate)).pow(totalPeriods).subtract(BigDecimal.ONE);
                return numerator.divide(denominator, 2, RoundingMode.HALF_UP);

            case EQUAL_PRINCIPAL:
                // For EQUAL_PRINCIPAL, the monthly payment is calculated as follows:
                // Monthly Principal = P / n = loanAmount / totalPeriods is constant
                // Remaining Principal = loanAmount - (Monthly Principal * repaidPeriods)
                // Monthly Interest = Remaining Principal * r = remainingPrincipal * monthlyRate
                // Monthly Payment = Monthly Principal + Monthly Interest
                BigDecimal monthlyPrincipal = loanAmount.divide(BigDecimal.valueOf(totalPeriods),20, RoundingMode.HALF_UP);
                BigDecimal remainingPrincipal = loanAmount.subtract(monthlyPrincipal.multiply(BigDecimal.valueOf(period-1))); //remainingPrincipal=loanAmount-(monthlyPrincipal*(period-1))
                BigDecimal interest = remainingPrincipal.multiply(monthlyRate); //interest=remainingPrincipal*monthlyRate
                return monthlyPrincipal.add(interest).setScale(2, RoundingMode.HALF_UP); //monthlyPayment=monthlyPrincipal+interest

            case EQUAL_PRINCIPAL_AND_INTEREST:
                //monthly payment= (loanAmount + totalInterest) / totalPeriods
                //monthly payment is fixed
                BigDecimal totalInterest = loanAmount.multiply(monthlyRate).multiply(BigDecimal.valueOf(totalPeriods));
                BigDecimal totalrepayment = loanAmount.add(totalInterest);
                return totalrepayment.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); // For EQUAL_PRINCIPAL_AND_INTEREST, the monthly payment is fixed

            case INTEREST_BEFORE_PRINCIPAL:
                BigDecimal monthlyInterest = loanAmount.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                if (period <totalPeriods) {
                    // For INTEREST_BEFORE_PRINCIPAL, the monthly payment is just the interest for the first totalPeriods - 1 periods
                    return monthlyInterest; // Only interest is paid in the first totalPeriods - 1 periods
                } else { //period == totalPeriods, the last period
                    // In the last period, the full loan amount is repaid along with the last interest
                    return loanAmount.add(monthlyInterest).setScale(2, RoundingMode.HALF_UP); // Last payment includes principal and interest
                }
            default:
                throw new IllegalArgumentException("Unknown repayment type: " + repaymentType); // For other repayment types
        }
    }

    public BigDecimal calculateTotalRepayment() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 1; i <= totalPeriods; i++) {
            total = total.add(getMonthlyRepayment(i));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public void checkAndUpdateStatus() {
        if(remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            this.isEnded = true;
            this.remainingAmount = BigDecimal.ZERO;
        } else {
            this.isEnded = false;
        }
    }

}



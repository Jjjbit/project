package com.ledger.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.MonthDay;

@Entity
@DiscriminatorValue("LoanAccount")
public class LoanAccount extends Account {

    public enum RepaymentType {
        EQUAL_INTEREST,
        EQUAL_PRINCIPAL,
        EQUAL_PRINCIPAL_AND_INTEREST,
        INTEREST_BEFORE_PRINCIPAL
    }

    @Column(name = "total_periods", nullable = false)
    @Max(value = 480, message = "Total periods cannot exceed 480")
    private int totalPeriods;

    @Column(name = "repaid_periods", nullable = false)
    private int repaidPeriods;

    @Column(name = "annual_interest_rate", precision = 3, scale = 2)
    private BigDecimal annualInterestRate;

    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @ManyToOne
    @JoinColumn(name = "receiving_account_id")
    private Account receivingAccount;

    @Column(name = "repayment_date")
    private MonthDay repaymentDate;

    @Enumerated(EnumType.STRING)
    private RepaymentType repaymentType;

    public LoanAccount() {}
    public LoanAccount(
            String name,
            BigDecimal balance,
            User owner,
            String note,
            Currency currency,
            boolean includedInNetWorth,
            boolean selectable,
            int totalPeriods,
            int repaidPeriods,
            BigDecimal interestRate,
            BigDecimal loanAmount,
            Account receivingAccount,
            MonthDay repaymentDate,
            RepaymentType repaymentType) {
        super(name, balance, AccountType.LOAN, AccountCategory.CREDIT, owner, currency, note, includedInNetWorth, selectable);
        this.totalPeriods = totalPeriods;
        this.repaidPeriods = repaidPeriods;
        this.annualInterestRate = interestRate;
        this.loanAmount = loanAmount;
        this.receivingAccount = receivingAccount;
        this.repaymentDate = repaymentDate;
        if (repaymentType==null){
            this.repaymentType = RepaymentType.EQUAL_INTEREST;
        }else{
            this.repaymentType = repaymentType;
        }

    }

    public BigDecimal getMonthlyRate() {
        if (annualInterestRate == null) return BigDecimal.ZERO;
        return annualInterestRate
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
    }

    public void repay(Account fromAccount) {
        BigDecimal monthlyRepayment = getMonthlyRepayment(repaidPeriods + 1);
        if(fromAccount != null) {
            fromAccount.debit(monthlyRepayment);
        }else {
            this.owner.updateNetAssetsAndLiabilities(monthlyRepayment);
        }
        this.repaidPeriods++;
    }
    public BigDecimal getRemainingAmount() {
        //remaining amount = loanAmount - (monthly repayment * repaid periods)
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
        switch(this.repaymentType) {
            case EQUAL_INTEREST:
                // For EQUAL_INTEREST, the monthly repayment is calculated as follows:
                // M = (P * r * (1 + r)^n) / ((1 + r)^n - 1)
                BigDecimal numerator = loanAmount.multiply(monthlyRate).multiply(
                        (BigDecimal.ONE.add(monthlyRate)).pow(totalPeriods)
                );
                BigDecimal denominator = (BigDecimal.ONE.add(monthlyRate)).pow(totalPeriods).subtract(BigDecimal.ONE);
                return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
            case EQUAL_PRINCIPAL:
                // For EQUAL_PRINCIPAL, the monthly payment is calculated as follows:
                // Monthly Principal = P / n
                // Monthly Interest = Remaining Principal * r
                // Monthly Payment = Monthly Principal + Monthly Interest
                BigDecimal monthlyPrincipal = loanAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP);
                BigDecimal remainingPrincipal = loanAmount.subtract(monthlyPrincipal.multiply(BigDecimal.valueOf(period-1))); //remainingPrincipal=loanAmount-(monthlyPrincipal*(period-1))
                BigDecimal interest = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP); //interest=remainingPrincipal*monthlyRate
                return monthlyPrincipal.add(interest).setScale(2, RoundingMode.HALF_UP); //monthlyPayment=monthlyPrincipal+interest
            case EQUAL_PRINCIPAL_AND_INTEREST:
                //monthly payment= (loanAmount + totalInterest) / totalPeriods
                //monthly payment is fixed
                BigDecimal totalInterest = loanAmount.multiply(monthlyRate).multiply(BigDecimal.valueOf(totalPeriods));
                BigDecimal totalrepayment = loanAmount.add(totalInterest);
                BigDecimal fixedPayment = totalrepayment.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP);
                return fixedPayment.setScale(2, RoundingMode.HALF_UP); // For EQUAL_PRINCIPAL_AND_INTEREST, the monthly payment is fixed
            case INTEREST_BEFORE_PRINCIPAL:
                BigDecimal monthlyInterest = loanAmount.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                if (period <totalPeriods) {
                    // For INTEREST_BEFORE_PRINCIPAL, the monthly payment is just the interest for the first totalPeriods - 1 periods
                    return monthlyInterest; // Only interest is paid in the first totalPeriods - 1 periods
                } else {
                    // In the last period, the full loan amount is repaid along with the last interest
                    return loanAmount.add(monthlyInterest).setScale(2, RoundingMode.HALF_UP); // Last payment includes principal and interest
                }
            default:
                throw new IllegalArgumentException("Unknown repayment type: " + repaymentType); // For other repayment types
        }
    }

    public BigDecimal calculateTotalRepayment() {
        //EQUAL_INTEREST
        // monthly repayment: M = P * r * (1 + r)^n / ((1 + r)^n - 1)
        //total repayment = monthly payment * total periods
        if (this.repaymentType == RepaymentType.EQUAL_INTEREST) {
            return calculateEqualInterestRepayment();
        }
        //EQUAL_PRINCIPAL
        // monthly payment: Monthly Principal = P / n
        if(this.repaymentType == RepaymentType.EQUAL_PRINCIPAL) {
            return calculateEqualPrincipalRepayment();
        }
        //EQUAL_PRINCIPAL_AND_INTEREST
        if(this.repaymentType == RepaymentType.EQUAL_PRINCIPAL_AND_INTEREST) {
            return calculateEqualPrincipalAndInterestRepayment();
        }
        //INTEREST_BEFORE_PRINCIPAL
        if(this.repaymentType == RepaymentType.INTEREST_BEFORE_PRINCIPAL) {
            return calculateInterestBeforePrincipalRepayment();
        }
        // If no repayment type matches, return zero
        throw new IllegalArgumentException("Unknown repayment type: " + repaymentType);
    }

    private BigDecimal calculateEqualInterestRepayment() {
        BigDecimal monthlyRate = getMonthlyRate();
        BigDecimal numerator = loanAmount.multiply(monthlyRate).multiply(
                (BigDecimal.ONE.add(monthlyRate)).pow(totalPeriods)
        );
        BigDecimal denominator = (BigDecimal.ONE.add(monthlyRate)).pow(totalPeriods).subtract(BigDecimal.ONE);
        BigDecimal monthlyPayment = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
        return monthlyPayment.multiply(BigDecimal.valueOf(totalPeriods)).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal calculateEqualPrincipalRepayment() {
        BigDecimal monthlyRate = getMonthlyRate();
        BigDecimal monthlyPrincipal = loanAmount.divide(BigDecimal.valueOf(totalPeriods), 2, RoundingMode.HALF_UP); //monthlyPrincipal=loanAmount/totalPeriods
        BigDecimal count= BigDecimal.ZERO;
        for (int i = 1; i <= totalPeriods; i++) {
            BigDecimal remainingPrincipal = loanAmount.subtract(monthlyPrincipal.multiply(BigDecimal.valueOf(i - 1))); //remainingPrincipal=loanAmount-(monthlyPrincipal*(i-1))
            BigDecimal interest = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP); //interest=remainingPrincipal*monthlyRate
            BigDecimal payment = monthlyPrincipal.add(interest); //payment=monthlyPrincipal+interest
            count = count.add(payment);
        }
        return count.setScale(2, RoundingMode.HALF_UP); //total repayment
    }
    private BigDecimal calculateEqualPrincipalAndInterestRepayment() {
        BigDecimal monthlyRate = getMonthlyRate();
        //totalInterest+loanAmount
        BigDecimal totalInterest = loanAmount.multiply(monthlyRate).multiply(BigDecimal.valueOf(totalPeriods)); //totalInterest=loanAmount*monthlyRate*totalPeriods
        return totalInterest.add(loanAmount).setScale(2, RoundingMode.HALF_UP); // total repayment=fixedPayment*totalPeriods
    }
    private BigDecimal calculateInterestBeforePrincipalRepayment() {
        BigDecimal monthlyRate = getMonthlyRate();
        //(totalPeriods - 1) * monthlyInterest + (loanAmount + monthlyInterest)
        BigDecimal monthlyInterest=loanAmount.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP); //monthlyInterest=loanAmount*monthlyRate
        // For the first totalPeriods - 1 periods, only interest is paid
        BigDecimal interestBeforeFinal = monthlyInterest.multiply(BigDecimal.valueOf(totalPeriods - 1)); //interestBeforeFinal=monthlyInterest*(totalPeriods-1)
        // In the last period, the full loan amount is repaid along with the last interest
        BigDecimal finalPayment = loanAmount.add(monthlyInterest); //finalPayment=loanAmount+monthlyInterest
        return interestBeforeFinal.add(finalPayment).setScale(2, RoundingMode.HALF_UP); // total repayment=interestBeforeFinal+finalPayment
    }

}



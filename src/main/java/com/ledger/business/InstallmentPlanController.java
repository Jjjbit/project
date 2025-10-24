package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.InstallmentPlanDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;

public class InstallmentPlanController {
    private InstallmentPlanDAO installmentPlanDAO;
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;

    public InstallmentPlanController(InstallmentPlanDAO installmentPlanDAO,
                                     TransactionDAO transactionDAO,
                                     AccountDAO accountDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.installmentPlanDAO = installmentPlanDAO;
    }

    public InstallmentPlan createInstallmentPlan(CreditAccount creditAccount,
                                         String name,
                                         BigDecimal totalAmount,
                                         int totalPeriods,
                                         int repaidPeriods,
                                         BigDecimal feeRate,
                                         InstallmentPlan.FeeStrategy feeStrategy,
                                         LocalDate repaymentStartDate,
                                         Ledger ledger,
                                         LedgerCategory category) throws SQLException {
        /*int repaidPeriods = 0;
        LocalDate today = LocalDate.now();
        if (repaymentStartDate.isBefore(today)) {
            repaidPeriods = (int) ChronoUnit.MONTHS.between(repaymentStartDate, today);
            if (repaidPeriods > totalPeriods) repaidPeriods = totalPeriods;
        }*/


        InstallmentPlan plan = new InstallmentPlan(
                name,
                totalAmount,
                totalPeriods,
                feeRate,
                repaidPeriods,
                feeStrategy,
                creditAccount,
                repaymentStartDate
        );
        installmentPlanDAO.insert(plan); //insert to db
        creditAccount.addInstallmentPlan(plan); //increase current debt
        accountDAO.update(creditAccount); //update current debt in db

        /*for (int i = 0; i < repaidPeriods; i++) {
            LocalDate paidDate = repaymentStartDate.plusMonths(i);
            BigDecimal paidAmount = plan.getMonthlyPayment(i + 1);

            Transaction tx = new Expense(
                    paidDate,
                    paidAmount,
                    "Installment payment " + (i + 1) + " " + name,
                    creditAccount,
                    ledger,
                    category
            );
            transactionDAO.insert(tx);
            creditAccount.debit(paidAmount);
            creditAccount.getOutgoingTransactions().add(tx);
            accountDAO.update(creditAccount); //update balance and current debt in db
            category.getTransactions().add(tx);

            if (ledger != null) {
                ledger.getTransactions().add(tx);
            }
        }

        for (int i = repaidPeriods; i < totalPeriods; i++) {
            LocalDate dueDate = repaymentStartDate.plusMonths(i);
            BigDecimal amount = plan.getMonthlyPayment(i + 1);

            Transaction tx = new Expense(
                    dueDate,
                    amount,
                    "Installment payment " + (i + 1) + " " + name,
                    creditAccount,
                    ledger,
                    category
            );

            if(LocalDate.now().isAfter(dueDate) || LocalDate.now().isEqual(dueDate)){
                transactionDAO.insert(tx);
                creditAccount.debit(amount);
                creditAccount.getOutgoingTransactions().add(tx);
                accountDAO.update(creditAccount); //update balance and current debt in db
                category.getTransactions().add(tx);

                if (ledger != null) {
                    ledger.getTransactions().add(tx);
                }
            }

        }*/
        return plan;
    }

    public boolean deleteInstallmentPlan(InstallmentPlan plan) throws SQLException {
        if (plan == null) {
            throw new IllegalArgumentException("Installment plan cannot be null");
        }

        CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
        BigDecimal remainingAmount = plan.getRemainingAmount();
        BigDecimal repaidAmount = plan.getTotalPayment().subtract(remainingAmount);
        creditAccount.removeInstallmentPlan(plan); ///reduce current debt
        creditAccount.credit(repaidAmount); //
        accountDAO.update(creditAccount); //update current debt and balance in db
        plan.setLinkedAccount(null);

        return installmentPlanDAO.delete(plan);
    }

    public boolean editInstallmentPlan(InstallmentPlan plan,
                                       BigDecimal totalAmount,
                                       Integer totalPeriods,
                                       Integer paidPeriods,
                                       BigDecimal feeRate,
                                       InstallmentPlan.FeeStrategy feeStrategy) throws SQLException {
        if (plan == null) {
            throw new IllegalArgumentException("Installment plan cannot be null");
        }
        if (totalAmount != null) {
            plan.setTotalAmount(totalAmount);
        }

        if (totalAmount != null){
            plan.setTotalAmount(totalAmount);
        }
        if (totalPeriods != null){
            plan.setTotalPeriods(totalPeriods);
        }
        if (paidPeriods != null){
            plan.setPaidPeriods(paidPeriods);
        }
        if (feeRate != null){
            plan.setFeeRate(feeRate);
        }
        if (feeStrategy != null){
            plan.setFeeStrategy(feeStrategy);
        }
        plan.setRemainingAmount(plan.getRemainingAmountWithRepaidPeriods());

        BigDecimal oldRemainingAmount = plan.getRemainingAmount();
        CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
        creditAccount.setCurrentDebt(creditAccount.getCurrentDebt().subtract(oldRemainingAmount).add(plan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP));
        accountDAO.update(creditAccount); //update current debt in db
        return installmentPlanDAO.update(plan); //update plan in db
    }

    public boolean payInstallment(InstallmentPlan plan, Ledger ledger) throws SQLException {
        if (plan == null) {
            throw new IllegalArgumentException("Installment plan cannot be null");
        }
        if (plan.getPaidPeriods() >= plan.getTotalPeriods()) {
            throw new IllegalStateException("All installments have been paid");
        }
        if(plan.getLinkedAccount() == null){
            throw new IllegalStateException("Linked account is null");
        }
        if(!ledger.getOwner().equals(plan.getLinkedAccount().getOwner())){
            throw new SecurityException("Ledger does not belong to the account owner");
        }

        BigDecimal paymentAmount = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
        CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();

        Transaction tx = new Transfer(
                LocalDate.now(),
                "Installment payment " + (plan.getPaidPeriods() + 1) + " " + plan.getName(),
                creditAccount,
                null,
                paymentAmount,
                ledger
        );
        transactionDAO.insert(tx); //insert transaction to db

        plan.repayOnePeriod(); //update remaining amount and paid periods
        creditAccount.debit(paymentAmount); //reduce balance or increase debt
        creditAccount.getOutgoingTransactions().add(tx);
        accountDAO.update(creditAccount); //update balance and current debt in db

        return installmentPlanDAO.update(plan); //update plan in db
    }


}

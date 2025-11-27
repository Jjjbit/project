package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.InstallmentDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class InstallmentController {
    private final InstallmentDAO installmentDAO;
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;

    public InstallmentController(InstallmentDAO installmentDAO, TransactionDAO transactionDAO,
                                 AccountDAO accountDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.installmentDAO = installmentDAO;
    }

    public Installment createInstallment(CreditAccount creditAccount, String name, BigDecimal totalAmount,
                                         int totalPeriods, BigDecimal interest, Installment.Strategy strategy,
                                         LocalDate repaymentStartDate, LedgerCategory category, Boolean includedInCurrentDebts,
                                         Ledger ledger) {
        if(creditAccount == null){
            return null;
        }
        if(name == null || name.isEmpty()){
            return null;
        }
        if(category == null){
            return null;
        }
        if(!category.getType().equals(CategoryType.EXPENSE)){
            return null;
        }
        if(strategy == null){
            strategy = Installment.Strategy.EVENLY_SPLIT;
        }
        if(totalAmount.compareTo(BigDecimal.ZERO) <= 0){
            return null;
        }
        if(totalPeriods <= 0){
            return null;
        }
        if(repaymentStartDate == null){
            repaymentStartDate = LocalDate.now();
        }

        if(includedInCurrentDebts == null){
            includedInCurrentDebts = true;
        }

        int repaidPeriods;
        LocalDate today = LocalDate.now();
        if (repaymentStartDate.isBefore(today)) {
            repaidPeriods = (int) ChronoUnit.MONTHS.between(repaymentStartDate, today); //number of month between dates

            int startDay = repaymentStartDate.getDayOfMonth();
            int todayDay = today.getDayOfMonth();

            if (todayDay > startDay) {
                repaidPeriods += 1;
            }

            if (repaidPeriods > totalPeriods) {
                repaidPeriods = totalPeriods;
            }

        }else if(repaymentStartDate.isEqual(today)){
            repaidPeriods = 1;
        }else{
            repaidPeriods = 0;
        }

        Installment plan = new Installment(name, totalAmount, totalPeriods,
                interest != null ? interest : BigDecimal.ZERO,
                repaidPeriods, strategy, creditAccount,
                repaymentStartDate, category,
                includedInCurrentDebts
        );
        installmentDAO.insert(plan); //insert to db
        if(includedInCurrentDebts){
            BigDecimal oldDebt = creditAccount.getCurrentDebt();
            creditAccount.setCurrentDebt(oldDebt.add(plan.getRemainingAmount()));
            accountDAO.update(creditAccount); //update current debt in db
        }

        if(repaidPeriods > 0){
            BigDecimal remainingAmount = plan.getRemainingAmountWithRepaidPeriods();
            BigDecimal repaidAmount = plan.getTotalPayment().subtract(remainingAmount);
            Transaction tx = new Expense(
                    LocalDate.now(),
                    repaidAmount,
                    "Record of already repaid installments for " + name,
                    creditAccount,
                    ledger,
                    category
            );
            transactionDAO.insert(tx); //insert transaction to db
            creditAccount.debit(repaidAmount); //reduce balance or increase debt
            accountDAO.update(creditAccount); //update balance and current debt in db
        }

        return plan;

    }

    public boolean deleteInstallment(Installment plan, CreditAccount account) {
        if (plan == null) {
            return false;
        }

        if(plan.isIncludedInCurrentDebts()){
            BigDecimal oldDebt = account.getCurrentDebt();
            account.setCurrentDebt(oldDebt.subtract(plan.getRemainingAmount()));
            accountDAO.update(account); //update current debt in db
        }

        return installmentDAO.delete(plan);
    }

    //includedInCurrentDebts is null means no change
    public boolean editInstallment(Installment plan, Boolean includedInCurrentDebts, CreditAccount account) {
        if(plan == null){
            return false;
        }

        if(includedInCurrentDebts != null && includedInCurrentDebts != plan.isIncludedInCurrentDebts()) {
            BigDecimal oldDebt = account.getCurrentDebt();

            if (includedInCurrentDebts) {
                account.setCurrentDebt(oldDebt.add(plan.getRemainingAmount()));
            } else {
                account.setCurrentDebt(oldDebt.subtract(plan.getRemainingAmount()));
            }
            plan.setIncludedInCurrentDebts(includedInCurrentDebts);
            accountDAO.update(account); //update current debt in db
            return installmentDAO.update(plan); //update plan in db
        }
        return true;
    }

    public boolean payInstallment(Installment plan, CreditAccount account) {
        if (plan == null) {
            return false;
        }
        if (plan.getPaidPeriods() >= plan.getTotalPeriods()) {
            return false; //all periods already paid
        }

        BigDecimal paymentAmount = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
        LedgerCategory category = plan.getCategory();
        Ledger ledger = category.getLedger();

        Transaction tx = new Expense(LocalDate.now(), paymentAmount,
                "Installment payment " + (plan.getPaidPeriods() + 1) + " " + plan.getName(),
                account, ledger, category);
        transactionDAO.insert(tx); //insert transaction to db

        plan.repayOnePeriod(); //update remaining amount and paid periods
        account.debit(paymentAmount); //reduce balance or increase debt
        if(plan.isIncludedInCurrentDebts()){
            BigDecimal oldDebt = account.getCurrentDebt();
            account.setCurrentDebt(oldDebt.subtract(paymentAmount));
        }
        accountDAO.update(account); //update balance and current debt in db

        return installmentDAO.update(plan); //update plan in db
    }
}

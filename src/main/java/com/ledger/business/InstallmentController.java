package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.InstallmentDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
                                         LocalDate repaymentStartDate, LedgerCategory category, Boolean includedInCurrentDebts) {
        try {
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
                repaidPeriods = (int) ChronoUnit.MONTHS.between(repaymentStartDate, today);

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
            creditAccount.getInstallmentPlans().add(plan);
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
                        category.getLedger(),
                        category
                );
                transactionDAO.insert(tx); //insert transaction to db
                creditAccount.debit(repaidAmount); //reduce balance or increase debt
                creditAccount.getTransactions().add(tx);
                category.getTransactions().add(tx);
                accountDAO.update(creditAccount); //update balance and current debt in db
            }

            return plan;

        }catch (SQLException e){
            System.err.println("SQL Exception during creating installment plan: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteInstallment(Installment plan) {
        try {
            if (plan == null) {
                return false;
            }
            if (installmentDAO.getById(plan.getId()) == null) {
                return false;
            }

            CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
            creditAccount.getInstallmentPlans().remove(plan);
            if(plan.isIncludedInCurrentDebts()){
                BigDecimal oldDebt = creditAccount.getCurrentDebt();
                creditAccount.setCurrentDebt(oldDebt.subtract(plan.getRemainingAmount()));
                accountDAO.update(creditAccount); //update current debt in db
            }

            return installmentDAO.delete(plan);
        } catch (SQLException e) {
            System.err.println("SQL Exception during deleting installment plan: " + e.getMessage());
            return false;
        }
    }

    public boolean editInstallment(Installment plan, Boolean includedInCurrentDebts) {
        if(plan == null){
            return false;
        }

        if(includedInCurrentDebts != null && includedInCurrentDebts != plan.isIncludedInCurrentDebts()) {
            CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
            if (includedInCurrentDebts) {
                BigDecimal oldDebt = creditAccount.getCurrentDebt();
                creditAccount.setCurrentDebt(oldDebt.add(plan.getRemainingAmount()));
            } else {
                BigDecimal oldDebt = creditAccount.getCurrentDebt();
                creditAccount.setCurrentDebt(oldDebt.subtract(plan.getRemainingAmount()));
            }
            plan.setIncludedInCurrentDebts(includedInCurrentDebts);
            try {
                accountDAO.update(creditAccount); //update current debt in db
                return installmentDAO.update(plan); //update plan in db
            } catch (SQLException e) {
                System.err.println("SQL Exception during editing installment plan: " + e.getMessage());
                return false;
            }
        }
        return true;
    }


    public boolean payInstallment(Installment plan) {
        try {
            if (plan == null) {
                return false;
            }
            if (plan.getPaidPeriods() >= plan.getTotalPeriods()) {
                return false; //all periods already paid
            }
            if (plan.getLinkedAccount() == null) {
                return false;
            }


            BigDecimal paymentAmount = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
            CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
            LedgerCategory category = plan.getCategory();
            Ledger ledger = category.getLedger();

            Transaction tx = new Expense(
                    LocalDate.now(),
                    paymentAmount,
                    "Installment payment " + (plan.getPaidPeriods() + 1) + " " + plan.getName(),
                    creditAccount,
                    ledger,
                    category
            );
            transactionDAO.insert(tx); //insert transaction to db

            plan.repayOnePeriod(); //update remaining amount and paid periods
            creditAccount.debit(paymentAmount); //reduce balance or increase debt
            creditAccount.getTransactions().add(tx); //add transaction to account
            if(plan.isIncludedInCurrentDebts()){
                BigDecimal oldDebt = creditAccount.getCurrentDebt();
                creditAccount.setCurrentDebt(oldDebt.subtract(paymentAmount));
            }
            accountDAO.update(creditAccount); //update balance and current debt in db
            category.getTransactions().add(tx); //add transaction to category

            return installmentDAO.update(plan); //update plan in db
        }catch (SQLException e){
            System.err.println("SQL Exception during paying installment: " + e.getMessage());
            return false;
        }
    }

    public List<Installment> getActiveInstallments(CreditAccount account) {
        try {
            List<Installment> installments = installmentDAO.getByAccountId(account.getId()).stream()
                    .filter(plan -> plan.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            return installments;
        }catch (SQLException e){
            System.err.println("SQL Exception in getActiveInstallmentPlansByAccount: " + e.getMessage());
            return List.of();
        }
    }


}

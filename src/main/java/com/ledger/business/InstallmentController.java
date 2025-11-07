package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.InstallmentDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public Installment createInstallment(CreditAccount creditAccount, String name,
                                         BigDecimal totalAmount, int totalPeriods, Integer repaidPeriods,
                                         BigDecimal interest, Installment.Strategy strategy,
                                         LocalDate repaymentStartDate, LedgerCategory category) {
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

        if(repaidPeriods == null ){
            LocalDate today = LocalDate.now();
            if (repaymentStartDate.isBefore(today)) {
                repaidPeriods = (int) ChronoUnit.MONTHS.between(repaymentStartDate, today);
                if (repaidPeriods > totalPeriods) repaidPeriods = totalPeriods;
            }else{
                repaidPeriods = 0;
            }
        }else{
            if(repaidPeriods < 0){
                repaidPeriods = 0;
            }
            if(repaidPeriods > totalPeriods){
                repaidPeriods = totalPeriods;
            }
            if(repaymentStartDate.plusMonths(repaidPeriods).isAfter(LocalDate.now()) || repaymentStartDate.plusMonths(repaidPeriods).isEqual(LocalDate.now())){
                repaidPeriods = (int) ChronoUnit.MONTHS.between(repaymentStartDate, LocalDate.now());
            }
        }

        Installment plan = new Installment(
                name,
                totalAmount,
                totalPeriods,
                interest != null ? interest : BigDecimal.ZERO,
                repaidPeriods,
                strategy,
                creditAccount,
                repaymentStartDate,
                category
        );
        try {
            installmentDAO.insert(plan); //insert to db
            creditAccount.addInstallmentPlan(plan); //increase current debt
            accountDAO.update(creditAccount); //update current debt in db
            return plan;
        }catch (SQLException e){
            System.err.println("SQL Exception during creating installment plan: " + e.getMessage());
            return null;
        }
    }

    public boolean deleteInstallment(Installment plan) {
        try {
            if (plan == null) {
                throw new IllegalArgumentException("Installment plan cannot be null");
            }
            if (installmentDAO.getById(plan.getId()) == null) {
                throw new IllegalArgumentException("Installment plan does not exist in database");
            }

            CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
            BigDecimal remainingAmount = plan.getRemainingAmount();
            BigDecimal repaidAmount = plan.getTotalPayment().subtract(remainingAmount);
            creditAccount.removeInstallmentPlan(plan); ///reduce current debt
            creditAccount.credit(repaidAmount); //
            accountDAO.update(creditAccount); //update current debt and balance in db
            plan.setLinkedAccount(null);

            return installmentDAO.delete(plan);
        } catch (SQLException e) {
            System.err.println("SQL Exception during deleting installment plan: " + e.getMessage());
            return false;
        }
    }

    public boolean editInstallment(Installment plan, BigDecimal totalAmount, Integer totalPeriods,
                                   Integer paidPeriods, BigDecimal interest,
                                   Installment.Strategy strategy, String name,
                                   LedgerCategory newCategory) {
        try {
            if (plan == null) {
                return false;
            }
            if (name != null && !name.isEmpty()) {
                plan.setName(name);
            }
            if (newCategory != null) {
                plan.setCategory(newCategory);
            }
            if (totalAmount != null) {
                plan.setTotalAmount(totalAmount);
            }

            if (totalAmount != null) {
                plan.setTotalAmount(totalAmount);
            }
            if (totalPeriods != null) {
                plan.setTotalPeriods(totalPeriods);
            }
            if (paidPeriods != null) {
                plan.setPaidPeriods(paidPeriods);
            }
            if (interest != null) {
                plan.setInterest(interest);
            }
            if (strategy != null) {
                plan.setStrategy(strategy);
            }
            BigDecimal oldRemainingAmount = plan.getRemainingAmount();
            plan.setRemainingAmount(plan.getRemainingAmountWithRepaidPeriods());

            CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
            creditAccount.setCurrentDebt(creditAccount.getCurrentDebt().subtract(oldRemainingAmount).add(plan.getRemainingAmount()).setScale(2, RoundingMode.HALF_UP));
            accountDAO.update(creditAccount); //update current debt in db
            return installmentDAO.update(plan); //update plan in db
        }catch (SQLException e){
            System.err.println("SQL Exception during editing installment plan: " + e.getMessage());
            return false;
        }
    }

    public boolean payInstallment(Installment plan, Ledger ledger) {
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
            if (!ledger.getOwner().equals(plan.getLinkedAccount().getOwner())) {
                return false;
            }

            BigDecimal paymentAmount = plan.getMonthlyPayment(plan.getPaidPeriods() + 1);
            CreditAccount creditAccount = (CreditAccount) plan.getLinkedAccount();
            LedgerCategory category = plan.getCategory();

            Transaction tx = new Expense(
                    LocalDate.now(),
                    paymentAmount,
                    "Installment payment " + (plan.getPaidPeriods() + 1) + " " + plan.getName(),
                    creditAccount,
                    ledger,
                    plan.getCategory()
            );
            transactionDAO.insert(tx); //insert transaction to db

            plan.repayOnePeriod(); //update remaining amount and paid periods
            creditAccount.debit(paymentAmount); //reduce balance or increase debt
            creditAccount.getOutgoingTransactions().add(tx);
            accountDAO.update(creditAccount); //update balance and current debt in db

            category.getTransactions().add(tx);

            return installmentDAO.update(plan); //update plan in db
        }catch (SQLException e){
            System.err.println("SQL Exception during paying installment: " + e.getMessage());
            return false;
        }
    }

    public List<Installment> getActiveInstallments(CreditAccount account) {
        try {
            List<Installment> installments = installmentDAO.getByAccount(account).stream()
                    .filter(plan -> plan.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            return installments;
        }catch (SQLException e){
            System.err.println("SQL Exception in getActiveInstallmentPlansByAccount: " + e.getMessage());
            return List.of();
        }
    }


}

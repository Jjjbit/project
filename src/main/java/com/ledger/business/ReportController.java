package com.ledger.business;


import com.ledger.domain.*;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportController {
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;
    private final BudgetDAO budgetDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;

    public ReportController(TransactionDAO transactionDAO, AccountDAO accountDAO, BudgetDAO budgetDAO, LedgerCategoryDAO ledgerCategoryDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.budgetDAO = budgetDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
    }

    public BigDecimal getTotalExpenseByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) {
        return transactionDAO.getByLedgerId(ledger.getId()).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalIncomeByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) {
        return transactionDAO.getByLedgerId(ledger.getId()).stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalExpenseByAccount(Account account, LocalDate startDate, LocalDate endDate) {
        return transactionDAO.getByAccountId(account.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .filter(t -> t.getFromAccount() != null &&
                        t.getFromAccount().getId()==account.getId())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalIncomeByAccount(Account account,LocalDate startDate, LocalDate endDate) {
        return transactionDAO.getByAccountId(account.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .filter(t -> t.getToAccount() != null &&
                        t.getToAccount().getId() == account.getId())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalAssets(User user) {
        BigDecimal totalAssets = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof BasicAccount || account instanceof CreditAccount)
                .filter(account -> account.getIncludedInNetAsset())
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLending = getTotalLendingAmount(user);
        return totalAssets.add(totalLending);
    }

    public BigDecimal getTotalLiabilities(User user) {
        BigDecimal totalCreditDebt = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof CreditAccount)
                .filter(account -> account.getIncludedInNetAsset())
                .map(account -> ((CreditAccount) account).getCurrentDebt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalUnpaidLoan = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof LoanAccount)
                .filter(account -> account.getIncludedInNetAsset())
                .map(account -> ((LoanAccount) account).getRemainingAmount()) //get this.remainingAmount
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBorrowing = getTotalBorrowingAmount(user);
        return totalCreditDebt.add(totalUnpaidLoan).add(totalBorrowing);
    }

    public BigDecimal getTotalBorrowingAmount(User user) {
        return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof BorrowingAccount)
                .map(account -> (BorrowingAccount) account)
                .filter(Account::getIncludedInNetAsset)
                .map(BorrowingAccount::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalLendingAmount(User user) {
        return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof LendingAccount)
                .map(account -> (LendingAccount) account)
                .filter(Account::getIncludedInNetAsset)
                .map(LendingAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isOverBudget(Budget budget) {
        budget.refreshIfExpired();
        budgetDAO.update(budget);

        Ledger ledger = budget.getLedger();
        if (budget.getCategory() == null) { //ledger-level budget
            List<Transaction> transactions = transactionDAO.getByLedgerId(ledger.getId()).stream()
                    .filter(t -> t.getType() == TransactionType.EXPENSE)
                    .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                    .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                    .toList();
            BigDecimal totalExpenses = transactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalExpenses.compareTo(budget.getAmount()) > 0;
        } else { //budget is a category-level budget
            LedgerCategory category= budget.getCategory();
            List<Transaction> transactions = new ArrayList<>(transactionDAO.getByCategoryId(category.getId()).stream()
                    .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                    .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                    .toList());

            List<LedgerCategory> childCategories = ledgerCategoryDAO.getCategoriesByParentId(category.getId());
            for (LedgerCategory childCategory : childCategories) {
                transactions.addAll(transactionDAO.getByCategoryId(childCategory.getId()).stream()
                        .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                        .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                        .toList());
            }
            BigDecimal totalCategoryBudget = transactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalCategoryBudget.compareTo(budget.getAmount()) > 0; //>0: over budget
        }
    }
//
//    public BigDecimal getTotalPendingAmount(Ledger ledger) {
//        return reimbursementDAO.getByLedger(ledger).stream()
//                .filter(r -> !r.isEnded())
//                .map(Reimbursement::getRemainingAmount)
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//    }
//
//    public BigDecimal getTotalReimbursedAmount(Ledger ledger) {
//        return reimbursementDAO.getByLedger(ledger).stream()
//                .filter(Reimbursement::isEnded)
//                .map(r -> r.getAmount().subtract(r.getRemainingAmount()))
//                .reduce(BigDecimal.ZERO, BigDecimal::add);
//    }

}

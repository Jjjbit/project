package com.ledger.business;


import com.ledger.domain.*;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparing;

public class ReportController {
    private final TransactionDAO transactionDAO;
    private final AccountDAO accountDAO;
    private final LedgerDAO ledgerDAO;
    private final BudgetDAO budgetDAO;
    private final InstallmentDAO installmentDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;

    public ReportController(TransactionDAO transactionDAO,
                            AccountDAO accountDAO,
                            LedgerDAO ledgerDAO,
                            BudgetDAO budgetDAO,
                            InstallmentDAO installmentDAO,
                            LedgerCategoryDAO ledgerCategoryDAO) {
        this.transactionDAO = transactionDAO;
        this.accountDAO = accountDAO;
        this.ledgerDAO = ledgerDAO;
        this.budgetDAO = budgetDAO;
        this.installmentDAO = installmentDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
    }


    public List<Transaction> getTransactionsByLedgerInRangeDate(Ledger ledger, LocalDate startDate,
                                                                LocalDate endDate){
        return transactionDAO.getByLedgerId(ledger.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .sorted((comparing(Transaction::getDate).reversed()))
                .toList();
    }

    public List<Transaction> getTransactionsByAccountInRangeDate(Account account, LocalDate startDate,
                                                      LocalDate endDate) {
        return transactionDAO.getByAccountId(account.getId()).stream()
                .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                .sorted((comparing(Transaction::getDate).reversed()))
                .toList();

    }

    public BigDecimal getTotalExpenseByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) {
        return getTransactionsByLedgerInRangeDate(ledger, startDate, endDate).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalIncomeByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) {
        return getTransactionsByLedgerInRangeDate(ledger, startDate, endDate).stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalExpenseByAccount(Account account, LocalDate startDate, LocalDate endDate) {
        return getTransactionsByAccountInRangeDate(account, startDate, endDate).stream()
                .filter(t -> t.getFromAccount() != null &&
                        t.getFromAccount().getId()==account.getId())

                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalIncomeByAccount(Account account,LocalDate startDate, LocalDate endDate) {
        return getTransactionsByAccountInRangeDate(account, startDate, endDate).stream()
                .filter(t -> t.getToAccount() != null &&
                        t.getToAccount().getId() == account.getId())
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<BorrowingAccount> getActiveBorrowingAccounts(User user) {
        return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof BorrowingAccount)
                .map(account -> (BorrowingAccount) account)
                .filter(account -> !account.getHidden())
                .toList();
    }

    public List<LendingAccount> getActiveLendingAccounts(User user) {
        return accountDAO.getAccountsByOwnerId(user.getId()).stream()
            .filter(account -> account instanceof LendingAccount)
            .map(account -> (LendingAccount) account)
            .filter(account-> !account.getHidden())
            .toList();
    }

    public BigDecimal getTotalAssets(User user) {
        BigDecimal totalAssets = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof BasicAccount || account instanceof CreditAccount)
                .filter(account -> account.getIncludedInNetAsset() && !account.getHidden())
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLending = getActiveLendingAccounts(user).stream()
                .filter(Account::getIncludedInNetAsset)
                .filter(account -> !account.getIsEnded())
                .map(LendingAccount::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalAssets.add(totalLending);
    }

    public BigDecimal getTotalLiabilities(User user) {
        BigDecimal totalCreditDebt = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof CreditAccount)
                .filter(account -> account.getIncludedInNetAsset() && !account.getHidden())
                .map(account -> ((CreditAccount) account).getCurrentDebt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalUnpaidLoan = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof LoanAccount)
                .filter(account -> account.getIncludedInNetAsset() && !account.getHidden())
                .map(account -> ((LoanAccount) account).getRemainingAmount()) //get this.remainingAmount
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBorrowing = getActiveBorrowingAccounts(user).stream()
                .filter(Account::getIncludedInNetAsset)
                .filter(account -> !account.getIsEnded())
                .map(BorrowingAccount::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInstallmentDebt = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof CreditAccount)
                .filter(account -> account.getIncludedInNetAsset() && !account.getHidden())
                .map(account -> installmentDAO.getByAccountId(account.getId()).stream()
                            .filter(plan -> !plan.isIncludedInCurrentDebts())
                            .map(Installment::getRemainingAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalCreditDebt.add(totalUnpaidLoan).add(totalBorrowing).add(totalInstallmentDebt);
    }

    public List<Account> getVisibleAccounts(User user) { //get visible BasicAccount, CreditAccount, LoanAccount
        return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof BasicAccount
                        || account instanceof CreditAccount
                        || account instanceof LoanAccount)
                .filter(account -> !account.getHidden())
                .toList();
    }

    public List<Ledger> getLedgersByUser(User user) {
        return ledgerDAO.getLedgersByUserId(user.getId());
    }

    public List<Installment> getActiveInstallments(CreditAccount account) {
        return installmentDAO.getByAccountId(account.getId()).stream()
                .filter(plan -> plan.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    public List<LedgerCategory> getLedgerCategoryTreeByLedger(Ledger ledger) {
        return ledgerCategoryDAO.getTreeByLedgerId(ledger.getId());
    }

    public Budget getActiveBudgetByLedger(Ledger ledger, Budget.Period period) {
        Budget budget = budgetDAO.getBudgetByLedgerId(ledger.getId(), period);
        if(budget!=null){
            budget.refreshIfExpired();
            budgetDAO.update(budget);
        }
        return budget;
    }

    public Budget getActiveBudgetByCategory(LedgerCategory category, Budget.Period period) {
        Budget budget = budgetDAO.getBudgetByCategoryId(category.getId(), period);
        if(budget!=null){
            budget.refreshIfExpired();
            budgetDAO.update(budget);
        }
        return budget;
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

}

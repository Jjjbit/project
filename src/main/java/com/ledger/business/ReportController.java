package com.ledger.business;


import com.ledger.domain.*;
import com.ledger.orm.*;

import java.math.BigDecimal;
import java.sql.SQLException;
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
        try {
            return transactionDAO.getByLedgerId(ledger.getId()).stream()
                    .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                    .sorted((comparing(Transaction::getDate).reversed()))
                    .toList();
        } catch (SQLException e) {
            System.err.println("SQL Exception in getTransactionsByLedgerInRangeDate: " + e.getMessage());
            return List.of();
        }
    }

    public List<Transaction> getTransactionsByAccountInRangeDate(Account account, LocalDate startDate,
                                                      LocalDate endDate) {
        try {
            return transactionDAO.getByAccountId(account.getId()).stream()
                    .filter(t -> !t.getDate().isBefore(startDate) && !t.getDate().isAfter(endDate))
                    .sorted((comparing(Transaction::getDate).reversed()))
                    .toList();
        } catch (SQLException e) {
            System.err.println("SQL Exception in getTransactionsByAccountInRangeDate: " + e.getMessage());
            return List.of();
        }
    }

    public BigDecimal getTotalExpenseByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) {
        return getTransactionsByLedgerInRangeDate(ledger, startDate, endDate).stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //
    public BigDecimal getTotalIncomeByLedger(Ledger ledger, LocalDate startDate, LocalDate endDate) {
        return getTransactionsByLedgerInRangeDate(ledger, startDate, endDate).stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //
    public BigDecimal getTotalExpenseByAccount(Account account, LocalDate startDate, LocalDate endDate) {
        return getTransactionsByAccountInRangeDate(account, startDate, endDate).stream()
                .filter(t -> t.getFromAccount() != null &&
                        t.getFromAccount().getId().equals(account.getId()))

                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //
    public BigDecimal getTotalIncomeByAccount(Account account,LocalDate startDate, LocalDate endDate) {
        return getTransactionsByAccountInRangeDate(account, startDate, endDate).stream()
                .filter(t -> t.getToAccount() != null &&
                        t.getToAccount().getId().equals(account.getId()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    //
    public List<BorrowingAccount> getActiveBorrowingAccounts(User user) {
        try {
            return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                    .filter(account -> account instanceof BorrowingAccount)
                    .map(account -> (BorrowingAccount) account)
                    .filter(account -> !account.getHidden())
                    .filter(account -> !account.getIsEnded())
                    .toList();
        }catch (SQLException e){
            System.err.println("SQL Exception in getAccountByOwnerId: " + e.getMessage());
        }
        return List.of();
    }

    //
    public List<LendingAccount> getActiveLendingAccounts(User user) {
        try{
            return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                .filter(account -> account instanceof LendingAccount)
                .map(account -> (LendingAccount) account)
                .filter(account-> !account.getHidden())
                .filter(account -> !account.getIsEnded())
                .toList();

        }catch (SQLException e){
            System.err.println("SQL Exception in getAccountByOwnerId: " + e.getMessage());
            return List.of();
        }
    }

    public BigDecimal getTotalAssets(User user) {

        try {
            BigDecimal totalAssets = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                    .filter(account -> account instanceof BasicAccount || account instanceof CreditAccount)
                    .filter(account -> account.getIncludedInNetAsset() && !account.getHidden())
                    .map(Account::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalLending = getActiveLendingAccounts(user).stream()
                    .filter(Account::getIncludedInNetAsset)
                    .map(LendingAccount::getBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalAssets.add(totalLending);
        } catch (SQLException e) {
            System.err.println("SQL Exception in getAccountByOwnerId: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getTotalLiabilities(User user) {
        try {
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
                    .map(BorrowingAccount::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalInstallmentDebt = accountDAO.getAccountsByOwnerId(user.getId()).stream()
                    .filter(account -> account instanceof CreditAccount)
                    .filter(account -> account.getIncludedInNetAsset() && !account.getHidden())
                    .map(account -> {
                        try {
                            return installmentDAO.getByAccountId(account.getId()).stream()
                                    .filter(plan -> !plan.isIncludedInCurrentDebts())
                                    .map(Installment::getRemainingAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                        } catch (SQLException e) {
                            System.err.println("SQL Exception in getTotalLiabilities installment part: " + e.getMessage());
                            return BigDecimal.ZERO;
                        }
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return totalCreditDebt.add(totalUnpaidLoan).add(totalBorrowing).add(totalInstallmentDebt);
        }catch (SQLException e){
            System.err.println("SQL Exception in getAccountByOwnerId: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    //
    public List<Account> getAccountsNotHidden(User user) { //get visible BasicAccount, CreditAccount, LoanAccount
        try {
            return accountDAO.getAccountsByOwnerId(user.getId()).stream()
                    .filter(account -> account instanceof BasicAccount
                            || account instanceof CreditAccount
                            || account instanceof LoanAccount)
                    .filter(account -> !account.getHidden())
                    .toList();
        }catch (SQLException e){
            System.err.println("SQL Exception in getAccountByOwnerId: " + e.getMessage());
            return List.of();
        }
    }

    //
    public List<Ledger> getLedgerByUser(User user) {
        try {
            return ledgerDAO.getLedgersByUserId(user.getId());
        }catch (SQLException e){
            System.err.println("SQL Exception in getLedgerByUserId: " + e.getMessage());
            return List.of();
        }
    }

    //
    public List<Installment> getActiveInstallments(CreditAccount account) {
        try {
            return installmentDAO.getByAccountId(account.getId()).stream()
                    .filter(plan -> plan.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
        }catch (SQLException e){
            System.err.println("SQL Exception in getActiveInstallmentPlansByAccount: " + e.getMessage());
            return List.of();
        }
    }

    //
    public List<LedgerCategory> getLedgerCategoryTreeByLedger(Ledger ledger) {
        try {
            return ledgerCategoryDAO.getTreeByLedgerId(ledger.getId());
        }catch (SQLException e){
            System.err.println("SQL Exception in getLedgerCategoryTreeByLedgerId: " + e.getMessage());
            return List.of();
        }
    }

    public Budget getActiveBudgetByLedger(Ledger ledger, Budget.Period period) {
        try {
            Budget budget = budgetDAO.getBudgetByLedgerId(ledger.getId(), period);
            if(budget!=null){
                budget.refreshIfExpired();
                budgetDAO.update(budget);
            }
            return budget;
        }catch (SQLException e){
            System.err.println("SQL Exception in getActiveBudgetsByLedger: " + e.getMessage());
            return null;
        }

    }

    public Budget getActiveBudgetByCategory(LedgerCategory category, Budget.Period period) {
        try {
            Budget budget = budgetDAO.getBudgetByCategoryId(category.getId(), period);
            if(budget!=null){
                budget.refreshIfExpired();
                budgetDAO.update(budget);
            }
            return budget;
        }catch (SQLException e){
            System.err.println("SQL Exception in getActiveBudgetByCategory: " + e.getMessage());
            return null;
        }

    }

    public boolean isOverBudget(Budget budget) {
        budget.refreshIfExpired();
        try {
            budgetDAO.update(budget);
        } catch (SQLException e) {
            System.err.println("SQL Exception during budgetDAO.update: " + e.getMessage());
        }

        Ledger ledger = budget.getLedger();
        try {
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
                if (!budget.getCategory().getLedger().equals(ledger)) {
                    return false;
                }
                List<Transaction> transactions = new ArrayList<>(transactionDAO.getByLedgerId(ledger.getId()).stream()
                        .filter(t -> t.getType() == TransactionType.EXPENSE)
                        .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                        .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                        .filter(t -> t.getCategory() != null)
                        .filter(t -> t.getCategory().getId().equals(budget.getCategory().getId()))
                        .toList());
                LedgerCategory category= budget.getCategory();
                List<LedgerCategory> childCategories = ledgerCategoryDAO.getCategoriesByParentId(category.getId());
                for (LedgerCategory childCategory : childCategories) {
                    transactions.addAll(ledger.getTransactions().stream()
                            .filter(t -> t.getType() == TransactionType.EXPENSE)
                            .filter(t -> t.getDate().isAfter(budget.getStartDate().minusDays(1))) //inclusive start date
                            .filter(t -> t.getDate().isBefore(budget.getEndDate().plusDays(1))) //inclusive end date
                            .filter(t -> t.getCategory() != null)
                            .filter(t -> t.getCategory().getId().equals(childCategory.getId()))
                            .toList());
                }
                BigDecimal totalCategoryBudget = transactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                return totalCategoryBudget.compareTo(budget.getAmount()) > 0; //>0: over budget
            }
        }catch (SQLException e){
            System.err.println("SQL Exception in isOverBudget: " + e.getMessage());
            return false;
        }
    }

}

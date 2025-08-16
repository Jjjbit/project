package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class TransactionStatistics {
    private final TransactionDAO transactionDAO;

    public TransactionStatistics(TransactionDAO transactionDAO) {
        this.transactionDAO = transactionDAO;
    }

    private BigDecimal sumTransactions(List<Transaction> txs, TransactionType type) {
        return txs.stream()
                .filter(t-> t.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalIncomeOfLedgerForMonth(Ledger ledger, YearMonth month, User user) {
        return sumTransactions(
                transactionDAO.findByLedgerAndDateRange(user.getId(), ledger.getId(), month.atDay(1), month.atEndOfMonth()),
                TransactionType.INCOME
        );
    }
    public BigDecimal getTotalExpenseOfLedgerForMonth(Ledger ledger, YearMonth month, User user) {
        return sumTransactions(
                transactionDAO.findByLedgerAndDateRange(user.getId(), ledger.getId(), month.atDay(1), month.atEndOfMonth()),
                TransactionType.EXPENSE
        );
    }
    public BigDecimal getTotalIncomeOfLedgerForYear(Ledger ledger, int year, User user) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        return sumTransactions(
                transactionDAO.findByLedgerAndDateRange(user.getId(), ledger.getId(), startDate, endDate),
                TransactionType.INCOME
        );
    }
    public BigDecimal getTotalExpenseOfLedgerForYear(Ledger ledger, int year, User user) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);
        return sumTransactions(
                transactionDAO.findByLedgerAndDateRange(user.getId(), ledger.getId(), startDate, endDate),
                TransactionType.EXPENSE
        );
    }

    public BigDecimal getTotalIncomeOfUserForYear(User user, int year) {
        BigDecimal totalIncome = BigDecimal.ZERO;
        for (Ledger ledger : user.getLedgers()) {
            totalIncome= totalIncome.add(getTotalIncomeOfLedgerForYear(ledger, year, user));
        }
        return totalIncome;
    }

    public BigDecimal getTotalExpenseOfUserForYear(User user, int year) {
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (Ledger ledger : user.getLedgers()) {
            totalExpense = totalExpense.add(getTotalExpenseOfLedgerForYear(ledger, year, user));
        }
        return totalExpense;
    }

    public List<Transaction> getTransactionsForMonthByLedger(User user, Ledger ledger, YearMonth month){
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        return transactionDAO.findByLedgerAndDateRange(user.getId(), ledger.getId(), startDate, endDate);
    }

    public List<Transaction> getTransactionsForYearByLedger(User user, Ledger ledger, int year){
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return transactionDAO.findByLedgerAndDateRange(user.getId(), ledger.getId(), startDate, endDate);
    }
    public List<Transaction> getTransactionsForMonthByAccount(User user, Account account, YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        return transactionDAO.findByAccountAndDateRange(user.getId(), account.getId(), startDate, endDate);
    }


    public List<Transaction> getTransactionsForMonthByCategory(User user, CategoryComponent category,YearMonth month) {
        LocalDate startDate = month.atDay(1);
        LocalDate endDate = month.atEndOfMonth();

        return transactionDAO.findByCategoryAndDateRange(user.getId(), category.getId(), startDate, endDate);
    }
    public List<Transaction> getTransactionsForYearByCategory(User user, CategoryComponent category, int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        return transactionDAO.findByCategoryAndDateRange(user.getId(), category.getId(), startDate, endDate);
    }

}

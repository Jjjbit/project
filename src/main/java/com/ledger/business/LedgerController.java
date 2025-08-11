package com.ledger.business;

import com.ledger.domain.Ledger;
import com.ledger.domain.Transaction;
import com.ledger.domain.User;
import com.ledger.orm.LedgerDAO;
import com.ledger.orm.TransactionDAO;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public class LedgerController {
    private LedgerDAO ledgerDAO;
    private UserDAO userDAO;
    private TransactionDAO transactionDAO;

    public LedgerController(LedgerDAO ledgerDAO, UserDAO userDAO, TransactionDAO transactionDAO) {
        this.ledgerDAO = ledgerDAO;
        this.userDAO = userDAO;
        this.transactionDAO = transactionDAO;
    }

    @Transactional
    public void createLedger(Ledger ledger) {
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger cannot be null");
        }
        if (ledger.getName() == null || ledger.getName().isEmpty()) {
            throw new IllegalArgumentException("Ledger name cannot be null or empty");
        }
        if (ledger.getOwner() == null || ledger.getOwner().getId() == null) {
            throw new IllegalArgumentException("Ledger owner cannot be null");
        }

        User owner = userDAO.findById(ledger.getOwner().getId());
        if (owner == null) {
            throw new IllegalArgumentException("Owner not found");
        }

        ledgerDAO.save(ledger);
    }

    @Transactional
    public void createLedger(String name, Long ownerId) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Ledger name cannot be null or empty");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID cannot be null");
        }

        User owner = userDAO.findById(ownerId);
        if (owner == null) {
            throw new IllegalArgumentException("Owner not found");
        }

        Ledger ledger = new Ledger(name, owner);
        ledgerDAO.save(ledger);
    }

    @Transactional
    public void deleteLedger(Long id) {
        Ledger ledger = ledgerDAO.findById(id);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }

        List<Transaction> transactions = ledger.getTransactions();
        for (Transaction tx : transactions) {
            tx.setLedger(null);
        }
        ledger.getTransactions().clear();
        ledgerDAO.delete(id);
    }

    @Transactional
    public void updateLedger(Long id, String newName) {
        Ledger ledger = ledgerDAO.findById(id);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException("New ledger name cannot be null or empty");
        }
        ledger.setName(newName);
        ledgerDAO.update(ledger);
    }

    public Ledger getLedger(Long id) {
        return ledgerDAO.findById(id);
    }

    public List<Ledger> getLedgersByUserId(Long userId) {
        return userDAO.findById(userId).getLedgers();
    }


    public BigDecimal getTotalIncomeForMonthByLedgerId(Long ledgerId, YearMonth month) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getTotalIncomeForMonth(month);
    }
    public BigDecimal getTotalExpenseForMonthByLedgerId(Long ledgerId, YearMonth month) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getTotalExpenseForMonth(month);
    }

    public BigDecimal getRemainingForMonthByLedgerId(Long ledgerId, YearMonth month) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getRemainingForMonth(month);
    }

    public List<Transaction> getTransactionForYearByLedgerId(Long ledgerId, int year) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getTransactionsForYear(year);
    }

    public BigDecimal getTotalIncomeForYearByLedgerId(Long ledgerId, int year) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getTotalIncomeForYear(year);
    }


    public BigDecimal getTotalExpenseForYearByLedgerId(Long ledgerId, int year) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getTotalExpenseForYear(year);
    }

    public BigDecimal getRemainingForYearByLedgerId(Long ledgerId, int year) {
        Ledger ledger = ledgerDAO.findById(ledgerId);
        if (ledger == null) {
            throw new IllegalArgumentException("Ledger not found");
        }
        return ledger.getRemainingForYear(year);
    }

}

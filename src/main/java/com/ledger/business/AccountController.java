package com.ledger.business;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;
import com.ledger.domain.User;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public class AccountController {
    private AccountDAO accountDAO;
    private UserDAO userDAO;

    public AccountController(AccountDAO accountDAO, UserDAO userDAO) {
        this.accountDAO = accountDAO;
        this.userDAO = userDAO;
    }

    public Account getAccount(Long id) {
        return accountDAO.findById(id);
    }
    
    @Transactional
    public void deleteAccount(Long id) {
        Account account = accountDAO.findById(id);
        accountDAO.delete(id);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }
        User owner = account.getOwner();
        owner.deleteAccount(account);
        userDAO.update(owner);
    }

    @Transactional
    public void hideAccount(Long accountId) {
        Account account = accountDAO.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        }else{
            account.hide();
            accountDAO.update(account);
        }
    }
    @Transactional
    public void setIncludedInNetAsset(Long accountId, boolean included) {
        Account account = accountDAO.findById(accountId);
        if( account == null) {
            throw new IllegalArgumentException("Account not found");
        }else{
            account.setIncludedInNetAsset(included);
            accountDAO.update(account);
            userDAO.update(account.getOwner());
        }
    }

    @Transactional
    public void setSelectable(Long accountId, boolean selectable) {
        Account account = accountDAO.findById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("Account not found");
        } else {
            account.setSelectable(selectable);
            accountDAO.update(account);
        }
    }


    @Transactional
    public void debitAccount(Long accountId, BigDecimal amount) {
        Account account = accountDAO.findById(accountId);
        if (account != null) {
            account.debit(amount);
            accountDAO.update(account);
            userDAO.update(account.getOwner());
        } else {
            throw new IllegalArgumentException("Account not found");
        }
    }

    @Transactional
    public void creditAccount(Long accountId, BigDecimal amount) {
        Account account = accountDAO.findById(accountId);
        if (account != null) {
            account.credit(amount);
            accountDAO.update(account);
            userDAO.update(account.getOwner());
        } else {
            throw new IllegalArgumentException("Account not found");
        }
    }


    @Transactional
    public void updateAccount(Long accountId, Account updatedAccount) {
        Account account = accountDAO.findById(accountId);
        if (account != null) {
            account.setName(updatedAccount.getName());
            account.setNotes(updatedAccount.getNotes());
            account.setBalance(updatedAccount.getBalance());
            account.setIncludedInNetAsset(updatedAccount.getIncludedInNetAsset());
            account.setSelectable(updatedAccount.getSelectable());

            accountDAO.update(account);
            userDAO.update(account.getOwner());
        } else {
            throw new IllegalArgumentException("Account not found");
        }
    }

    public List<Transaction> getTransactionsForMonth(Long accountId, YearMonth month) {
        Account account = accountDAO.findById(accountId);
        return account.getTransactionsForMonth(month);
    }
}

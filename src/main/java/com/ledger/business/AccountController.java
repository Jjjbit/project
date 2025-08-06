package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public class AccountController {
    private AccountDAO accountDAO;
    private UserDAO userDAO;

    public AccountController(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    public void createAccount(Account account) {
        accountDAO.save(account);
    }
    public Account getAccount(Long id) {
        return accountDAO.findById(id);
    }
    public List<Account> getAccountsByUserId(Long userId) {
        return accountDAO.findByUserId(userId);
    }

    public void updateAccount(Account account) {
        accountDAO.update(account);
    }
    public void deleteAccount(Long id) {
        accountDAO.delete(id);
    }

    @Transactional
    public void hideAccount(Long accountId) {
        accountDAO.setHidden(accountId);
    }

    public List<Transaction> getTransactionsForMonth(Long accountId, YearMonth month) {
        return accountDAO.findTransactionsByAccountAndMonth(accountId, month);
    }

    @Transactional
    public void repayDeb(Long creditAccountId, BigDecimal amount, Long fromAccountId, Long userId) {
        CreditAccount creditAccount = (CreditAccount) accountDAO.findById(creditAccountId);
        Account fromAccount = (fromAccountId != null) ? accountDAO.findById(fromAccountId) : null;
        User user = userDAO.findById(userId);

        if (creditAccount != null) {
            creditAccount.repayDebt(amount, fromAccount);
            accountDAO.update(creditAccount);
            if (fromAccount != null) {
                accountDAO.update(fromAccount);
            }
            userDAO.update(user);
        }
    }

    @Transactional
    public void repayLoan(Long loanAccountId, Long fromAccountId) {
        LoanAccount loanAccount = (LoanAccount) accountDAO.findById(loanAccountId);
        Account fromAccount = (fromAccountId != null) ? accountDAO.findById(fromAccountId) : null;

        if (loanAccount != null) {
            loanAccount.repayLoan(fromAccount);
            accountDAO.update(loanAccount);
            if (fromAccount != null) {
                accountDAO.update(fromAccount);
            }
        }
    }



}

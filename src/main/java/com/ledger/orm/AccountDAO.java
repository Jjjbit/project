package com.ledger.orm;

import com.ledger.domain.Account;
import com.ledger.domain.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Transactional
public class AccountDAO {

    @PersistenceContext
    private EntityManager em;

    public void save(Account account) {
        em.persist(account);
    }
    public Account findById(Long id) {
        return em.find(Account.class, id);
    }

    public List<Account> findByUserId(Long userId) {
        return em.createQuery(
                        "SELECT a FROM Account a WHERE a.owner.id = :userId AND a.hidden = false", Account.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public void update(Account account) {
        em.merge(account);
    }

    public void delete(Long id) {
        Account account = em.find(Account.class, id);
        if (account != null) {
            em.remove(account);
        }
    }

    public List<Transaction> findTransactionsByAccountAndMonth(Long accountId, YearMonth month) {
        return em.createQuery(
                        "SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
                                "AND FUNCTION('YEAR', t.date) = :year " +
                                "AND FUNCTION('MONTH', t.date) = :month", Transaction.class)
                .setParameter("accountId", accountId)
                .setParameter("year", month.getYear())
                .setParameter("month", month.getMonthValue())
                .getResultList();
    }

    public void setHidden(Long accountId) {
        Account account = findById(accountId);
        if (account != null) {
            account.hide();
            em.merge(account);
        }
    }
    public void setIncludedInNetAsset(Long accountId, boolean included) {
        Account account = findById(accountId);
        if (account != null) {
            account.setIncludedInNetAsset(included);
            em.merge(account);
        }
    }
    public void setSelectable(Long accountId, boolean selectable) {
        Account account = findById(accountId);
        if (account != null) {
            account.setSelectable(selectable);
            em.merge(account);
        }
    }

    public void credit(Long accountId, BigDecimal amount) {
        Account account = findById(accountId);
        if (account != null) {
            account.setBalance(account.getBalance().add(amount));
            em.merge(account);
        }
    }

    public void debit(Long accountId, BigDecimal amount) {
        Account account = findById(accountId);
        if (account != null) {
            account.setBalance(account.getBalance().subtract(amount));
            em.merge(account);
        }
    }
}

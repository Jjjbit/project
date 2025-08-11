package com.ledger.orm;

import com.ledger.domain.Account;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;

@Transactional
public class AccountDAOImpl implements AccountDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Account account) {
        em.persist(account);
    }
    @Override
    public Account findById(Long id) {
        return em.find(Account.class, id);
    }

    @Override
    public List<Account> findByUserId(Long userId) {
        return em.createQuery(
                        "SELECT a FROM Account a WHERE a.owner.id = :userId AND a.hidden = false", Account.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public void update(Account account) {
        em.merge(account);
    }

    @Override
    public void delete(Long id) {
        Account account = em.find(Account.class, id);
        if (account != null) {
            em.remove(account);
        }
    }
}

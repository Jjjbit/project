package com.ledger.orm;

import com.ledger.domain.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;

@Transactional
public class TransactionDAOImpl implements TransactionDAO {
    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Transaction transaction) {
        em.persist(transaction);
    }

    @Override
    public Transaction findById(Long id) {
        return em.find(Transaction.class, id);
    }

    @Override
    public List<Transaction> findByAccountId(Long accountId) {
        return em.createQuery(
                "SELECT t FROM Transaction t WHERE t.account.id = :accountId", Transaction.class)
                .setParameter("accountId", accountId)
                .getResultList();
    }

    @Override
    public List<Transaction> findByCategory(Long categoryId) {
        return em.createQuery(
                "SELECT t FROM Transaction t WHERE t.category.id = :categoryId", Transaction.class)
                .setParameter("categoryId", categoryId)
                .getResultList();
    }

    @Override
    public List<Transaction> findByUserId(Long userId) {
        return em.createQuery(
                "SELECT t FROM Transaction t WHERE t.account.owner.id = :userId", Transaction.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public void update(Transaction transaction) {
        em.merge(transaction);
    }

    @Override
    public void delete(Long id) {
        Transaction transaction = em.find(Transaction.class, id);
        if (transaction != null) {
            em.remove(transaction);
        }
    }

}

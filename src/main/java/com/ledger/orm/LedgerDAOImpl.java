package com.ledger.orm;

import com.ledger.domain.Ledger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;

@Transactional
public class LedgerDAOImpl implements LedgerDAO {
    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Ledger ledger) {
        em.persist(ledger);
    }

    @Override
    public Ledger findById(Long id) {
        return em.find(Ledger.class, id);
    }

    @Override
    public List<Ledger> findLedgersByUserId(Long userId) {
        return em.createQuery("SELECT l FROM Ledger l WHERE l.owner.id = :userId", Ledger.class)
                 .setParameter("userId", userId)
                 .getResultList();
    }

    @Override
    public void update(Ledger ledger) {
        em.merge(ledger);
    }

    @Override
    public void delete(Long id) {
        Ledger ledger = em.find(Ledger.class, id);
        if (ledger != null) {
            em.remove(ledger);
        }
    }
}

package com.ledger.orm;

import com.ledger.domain.InstallmentPlan;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;

@Transactional
public class InstallmentPlanDAOImpl implements InstallmentPlanDAO {
    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(InstallmentPlan installmentPlan) {
        em.persist(installmentPlan);
    }

    @Override
    public InstallmentPlan findById(Long id) {
        return em.find(InstallmentPlan.class, id);
    }

    @Override
    public List<InstallmentPlan> findByAccountId(Long accountId) {
        return em.createQuery(
                "SELECT ip FROM InstallmentPlan ip WHERE ip.linkedAccount.id = :accountId", InstallmentPlan.class)
                .setParameter("accountId", accountId)
                .getResultList();
    }

    @Override
    public void update(InstallmentPlan installmentPlan) {
        em.merge(installmentPlan);
    }
    @Override
    public void delete(Long id) {
        InstallmentPlan installmentPlan = em.find(InstallmentPlan.class, id);
        if (installmentPlan != null) {
            em.remove(installmentPlan);
        }
    }

}

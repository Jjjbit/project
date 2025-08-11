package com.ledger.orm;

import com.ledger.domain.Budget;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;

@Transactional
public class BudgetDAOImpl implements BudgetDAO {
    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Budget budget) {
        em.persist(budget);
    }

    @Override
    public void update(Budget budget) {
        em.merge(budget);
    }

    @Override
    public void delete(Long id) {
        Budget budget = em.find(Budget.class, id);
        if (budget != null) {
            em.remove(budget);
        }
    }

    @Override
    public Budget findById(Long id) {
        return em.find(Budget.class, id);
    }
    @Override
    public List<Budget> findAll() {
        return em.createQuery("SELECT b FROM Budget b", Budget.class).getResultList();
    }

    @Override
    public Budget findByCategoryId(Long categoryId){
        LocalDate today = LocalDate.now();

        return em.createQuery("SELECT b FROM Budget b WHERE  b.category.id = :categoryId AND :today BETWEEN b.startDate AND b.endDate", Budget.class)
                .setParameter("categoryId", categoryId)
                .setParameter("today", today)
                .getResultStream().findFirst().orElse(null);
    }
}



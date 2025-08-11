package com.ledger.orm;

import com.ledger.domain.CategoryComponent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@Transactional
public class CategoryComponentDAOImpl implements CategoryComponentDAO{
    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(CategoryComponent categoryComponent) {
        em.persist(categoryComponent);
    }
    @Override
    public void update(CategoryComponent categoryComponent) {
        em.merge(categoryComponent);
    }
    @Override
    public void delete(Long id) {
        CategoryComponent categoryComponent = em.find(CategoryComponent.class, id);
        if (categoryComponent != null) {
            em.remove(categoryComponent);
        }
    }
    @Override
    public CategoryComponent findById(Long id) {
        return em.find(CategoryComponent.class, id);
    }
}

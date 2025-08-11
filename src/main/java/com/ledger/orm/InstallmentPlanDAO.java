package com.ledger.orm;

import com.ledger.domain.InstallmentPlan;

import java.util.List;

public interface InstallmentPlanDAO {
    void save(InstallmentPlan installmentPlan);
    InstallmentPlan findById(Long id);
    List<InstallmentPlan> findByAccountId(Long accountId);
    void update(InstallmentPlan installmentPlan);
    void delete(Long id);
}

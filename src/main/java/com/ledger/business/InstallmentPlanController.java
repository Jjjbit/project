package com.ledger.business;

import com.ledger.domain.InstallmentPlan;
import com.ledger.orm.InstallmentPlanDAO;

public class InstallmentPlanController {
    private InstallmentPlanDAO installmentPlanDAO;

    public InstallmentPlanController(InstallmentPlanDAO installmentPlanDAO) {
        this.installmentPlanDAO = installmentPlanDAO;
    }

    public void createInstallmentPlan(InstallmentPlan installmentPlan) {
        installmentPlanDAO.save(installmentPlan);
    }

}

package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.InstallmentPlanDAO;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.MonthDay;
import java.util.List;

public class CreditAccountController {
    private AccountDAO accountDAO;
    private InstallmentPlanDAO installmentPlanDAO;
    private UserDAO userDAO;

    public CreditAccountController(AccountDAO accountDAO, InstallmentPlanDAO installmentPlanDAO, UserDAO userDAO) {
        this.accountDAO = accountDAO;
        this.installmentPlanDAO = installmentPlanDAO;
        this.userDAO = userDAO;
    }

    @Transactional
    public void createCreditAccount(String name,
                                    BigDecimal balance,
                                    Long ownerId,
                                    String notes,
                                    boolean includedInNetWorth,
                                    boolean selectable,
                                    BigDecimal creditLimit,
                                    BigDecimal currentDebt, //contiene importo residuo delle rate
                                    MonthDay billDate,
                                    MonthDay dueDate,
                                    AccountType type) {
        CreditAccount creditAccount = new CreditAccount(name, balance, userDAO.findById(ownerId), notes, includedInNetWorth, selectable, creditLimit, currentDebt, billDate, dueDate, type);
        accountDAO.save(creditAccount);
        //userDAO.update(creditAccount.getOwner());
    }

    @Transactional
    public void repayDeb(Long creditAccountId, BigDecimal amount, Long fromAccountId) {
        CreditAccount creditAccount = (CreditAccount) accountDAO.findById(creditAccountId);
        Account fromAccount = (fromAccountId != null) ? accountDAO.findById(fromAccountId) : null;

        if (creditAccount != null) {
            creditAccount.repayDebt(amount, fromAccount);
            accountDAO.update(creditAccount);
            if (fromAccount != null) {
                accountDAO.update(fromAccount);
            }
            //userDAO.update(creditAccount.getOwner());
        }
    }

    @Transactional
    public void repayInstallmentPlan(Long accountId,Long installmentPlanId) {
        CreditAccount account = (CreditAccount) accountDAO.findById(accountId);
        InstallmentPlan installmentPlan = installmentPlanDAO.findById(installmentPlanId);

        if(account !=null){
            if (installmentPlan !=null) {
                account.repayInstallmentPlan(installmentPlan);
                accountDAO.update(account);
                //userDAO.update(account.getOwner());
                //installmentPlanDAO.update(installmentPlan);
            }else{
                throw new IllegalArgumentException("Installment plan not found");
            }
        }else{
            throw new IllegalArgumentException("Account not found");
        }
    }

    public List<InstallmentPlan> getInstallmentPlans(Long accountId) {
        CreditAccount account = (CreditAccount) accountDAO.findById(accountId);
        if (account != null) {
            return account.getInstallmentPlans();
        } else {
            throw new IllegalArgumentException("Account not found");
        }
    }

    public BigDecimal getCurrentDebt(Long accountId) {
        CreditAccount account = (CreditAccount) accountDAO.findById(accountId);
        if (account != null) {
            return account.getCurrentDebt();
        } else {
            throw new IllegalArgumentException("Account not found");
        }
    }
}

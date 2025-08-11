package com.ledger.business;

import com.ledger.domain.Account;
import com.ledger.domain.LoanAccount;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.MonthDay;

public class LoanAccountController {
    private AccountDAO accountDAO;
    private UserDAO userDAO;

    public LoanAccountController(AccountDAO accountDAO, UserDAO userDAO) {
        this.accountDAO = accountDAO;
        this.userDAO = userDAO;
    }

    @Transactional
    public void createLoanAccount(String name,
                                  Long ownerId,
                                  String notes,
                                  boolean includedInNetWorth,
                                  int totalPeriods,
                                  int repaidPeriods,
                                  BigDecimal interestRate,
                                  BigDecimal loanAmount,
                                  Account receivingAccount,
                                  MonthDay repaymentDate,
                                  LoanAccount.RepaymentType repaymentType) {
        LoanAccount loanAccount = new LoanAccount(name, userDAO.findById(ownerId), notes, includedInNetWorth, totalPeriods, repaidPeriods, interestRate, loanAmount, receivingAccount, repaymentDate, repaymentType);
        accountDAO.save(loanAccount);
        userDAO.update(loanAccount.getOwner());
    }

    @Transactional
    public void repayLoan(Long loanAccountId, Long fromAccountId) {
        LoanAccount loanAccount = (LoanAccount) accountDAO.findById(loanAccountId);
        Account fromAccount = (fromAccountId != null) ? accountDAO.findById(fromAccountId) : null;

        if (loanAccount != null) {
            loanAccount.repayLoan(fromAccount);
            accountDAO.update(loanAccount);
            if (fromAccount != null) {
                accountDAO.update(fromAccount);
            }
            userDAO.update(loanAccount.getOwner());
        }
    }
}

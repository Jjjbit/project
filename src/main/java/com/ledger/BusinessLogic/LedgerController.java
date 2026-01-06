package com.ledger.BusinessLogic;

import com.ledger.DomainModel.*;
import com.ledger.ORM.*;
import com.ledger.DbTransaction.DbTransactionManager;
import com.ledger.Session.UserSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class LedgerController {
    private final LedgerDAO ledgerDAO;
    private final TransactionDAO transactionDAO;
    private final CategoryDAO categoryDAO;
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final AccountDAO accountDAO;
    private final BudgetDAO budgetDAO;

    public LedgerController(LedgerDAO ledgerDAO, TransactionDAO transactionDAO, CategoryDAO categoryDAO,
                            LedgerCategoryDAO ledgerCategoryDAO, AccountDAO accountDAO, BudgetDAO budgetDAO) {
        this.budgetDAO = budgetDAO;
        this.accountDAO = accountDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
        this.categoryDAO = categoryDAO;
        this.ledgerDAO = ledgerDAO;
        this.transactionDAO = transactionDAO;
    }

    public List<Ledger> getLedgersByUser(User user) {
        return ledgerDAO.getLedgersByUserId(user.getId());
    }

    public Ledger createLedger(String name) {
        if(name == null || name.isEmpty() || name.length() > 50) return null;
        if(!UserSession.getInstance().isLoggedIn()) return null;
        User owner = UserSession.getInstance().getCurrentUser();
        if (ledgerDAO.getByNameAndOwnerId(name, owner.getId()) != null) return null;
        Ledger ledger = new Ledger(name, owner);
        return DbTransactionManager.getInstance().execute(() -> {
            if(!ledgerDAO.insert(ledger)) throw new Exception("Failed to create ledger");
            List<Category> templateCategories = new ArrayList<>(categoryDAO.getParentCategories()); //get only parents
            List<LedgerCategory> allCategories = new ArrayList<>();
            for (Category template : templateCategories) {
                allCategories.addAll(copyCategoryTree(template, ledger, null));
            }
            //create Budget for ledger level for each Period
            for (Period period : Period.values()) {
                Budget ledgerBudget = new Budget(BigDecimal.ZERO, period, null, ledger);
                if(!budgetDAO.insert(ledgerBudget)) throw new Exception("Failed to create ledger budget");
            }
            //create Budget for each default category
            for (LedgerCategory cat : allCategories) {
                if (cat.getType() == CategoryType.EXPENSE) {
                    for (Period period : Period.values()) {
                        Budget categoryBudget = new Budget(BigDecimal.ZERO, period, cat, ledger);
                        if(!budgetDAO.insert(categoryBudget)) throw new Exception("Failed to create category budget");
                    }
                }
            }
            return ledger;
        });
    }

    private List<LedgerCategory> copyCategoryTree(Category template, Ledger ledger, LedgerCategory parent) {
        List<LedgerCategory> result = new ArrayList<>();

        LedgerCategory copy = new LedgerCategory();
        copy.setName(template.getName());
        copy.setType(template.getType());
        copy.setLedger(ledger);
        copy.setParent(parent);

        if (!ledgerCategoryDAO.insert(copy)) throw new RuntimeException("cannot insert category: " + template.getName());
        result.add(copy);
        for (Category childTemplate : categoryDAO.getCategoriesByParentId(template.getId())) {
            List<LedgerCategory> childCopies = copyCategoryTree(childTemplate, ledger, copy);
            result.addAll(childCopies);
        }
        return result;
    }

    public boolean deleteLedger(Ledger ledger) {
        if(ledger == null) return false;
        Boolean deleted = DbTransactionManager.getInstance().execute(() -> {
            List<Transaction> transactionsToDelete = transactionDAO.getByLedgerId(ledger.getId());
            for (Transaction tx : transactionsToDelete) {
                Account to = null;
                Account from = null;
                if (tx.getToAccount() != null) to = accountDAO.getAccountById(tx.getToAccount().getId());
                if (tx.getFromAccount() != null) from = accountDAO.getAccountById(tx.getFromAccount().getId());
                switch (tx.getType()) {
                    case INCOME:
                        if (to != null) {
                            to.debit(tx.getAmount());
                            if(!accountDAO.update(to)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        break;
                    case EXPENSE:
                        if (from != null) {
                            from.credit(tx.getAmount());
                            if(!accountDAO.update(from)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        break;
                    case TRANSFER:
                        if (from != null) {
                            from.credit(tx.getAmount());
                            if(!accountDAO.update(from)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        if (to != null) {
                            to.debit(tx.getAmount());
                            if(!accountDAO.update(to)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        break;
                }
            }
            return ledgerDAO.delete(ledger);
        });
        return deleted != null && deleted;
    }

    public boolean renameLedger(Ledger ledger, String newName) {
        if(ledger == null) return false;
        if(newName == null || newName.isEmpty()) return false;
        if(!UserSession.getInstance().isLoggedIn()) return false;
        User user = UserSession.getInstance().getCurrentUser();
        Ledger existingLedger = ledgerDAO.getByNameAndOwnerId(newName, user.getId());
        if (existingLedger != null && existingLedger.getId() != ledger.getId()) return false;
        ledger.setName(newName);
        return ledgerDAO.update(ledger);
    }
}
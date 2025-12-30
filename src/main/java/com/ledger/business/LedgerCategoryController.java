package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.AccountDAO;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.LedgerCategoryDAO;
import com.ledger.orm.TransactionDAO;
import com.ledger.service.TransactionManager;

import java.math.BigDecimal;
import java.util.List;

public class LedgerCategoryController {
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final TransactionDAO transactionDAO;
    private final BudgetDAO budgetDAO;
    private final AccountDAO accountDAO;

    public LedgerCategoryController(LedgerCategoryDAO ledgerCategoryDAO,
                                    TransactionDAO transactionDAO, BudgetDAO budgetDAO, AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
        this.budgetDAO = budgetDAO;
        this.transactionDAO = transactionDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
    }

    public List<LedgerCategory> getCategoryTreeByLedger(Ledger ledger) {
        return ledgerCategoryDAO.getTreeByLedger(ledger).stream()
                .filter(category -> !category.getName().equals("Claim Income"))
                .toList();
    }

    public LedgerCategory createCategory(String name, Ledger ledger, CategoryType type) {
        if(ledger == null){
            return null;
        }
        if(name == null || name.isEmpty() || name.length() > 50) {
            return null;
        }
        if(type == null){
            return null;
        }
        LedgerCategory existingCategory = ledgerCategoryDAO.getByNameAndLedger(name, ledger);
        if(existingCategory!= null && existingCategory.getType() == type) {
            return null;
        }
        LedgerCategory category = new LedgerCategory(name, type, ledger);
//        if (!ledgerCategoryDAO.insert(category)) return null;
//        //create budget for ledgerCategory
//        for (Period period : Period.values()) {
//            Budget budget = new Budget(BigDecimal.ZERO, period, category, ledger);
//            budgetDAO.insert(budget);
//        }
//        return category;
        return TransactionManager.getInstance().execute(() -> {
            if (!ledgerCategoryDAO.insert(category)) throw new Exception("Failed to insert category");
            //create budget for ledgerCategory
            for (Period period : Period.values()) {
                Budget budget = new Budget(BigDecimal.ZERO, period, category, ledger);
                if(!budgetDAO.insert(budget)) throw new Exception("Failed to insert budget for category");
            }
            return category;
        });
    }

    public LedgerCategory createSubCategory(String name, LedgerCategory parentCategory) {
        if(parentCategory == null){
            return null;
        }
        if(parentCategory.getParent()!=null){
            return null;
        }
        if(name == null || name.isEmpty() || name.length() > 50) {
            return null;
        }
        Ledger ledger= parentCategory.getLedger();
        LedgerCategory existingCategory = ledgerCategoryDAO.getByNameAndLedger(name, ledger);
        if (existingCategory != null && existingCategory.getType() == parentCategory.getType()) {
            return null;
        }
        LedgerCategory category = new LedgerCategory(name, parentCategory.getType(), ledger);
        category.setParent(parentCategory);

        return  TransactionManager.getInstance().execute(() -> {
            if (!ledgerCategoryDAO.insert(category)) throw new Exception("Failed to insert sub-category");
            //create budget for ledgerCategory
            for (Period period : Period.values()) {
                Budget budget = new Budget(BigDecimal.ZERO, period, category, ledger);
                if(!budgetDAO.insert(budget)) throw new Exception("Failed to insert budget for sub-category");
            }
            return category;
        });
//        if (!ledgerCategoryDAO.insert(category)) return null;
//        //create budget for ledgerCategory
//        for (Period period : Period.values()) {
//            Budget budget = new Budget(BigDecimal.ZERO, period, category, ledger);
//            budgetDAO.insert(budget);
//        }
//        return category;
    }

    public boolean promoteSubCategory(LedgerCategory subCategory) {
        if (subCategory == null) {
            return false;
        }
        if (subCategory.getParent() == null) {
            return false;
        }
        subCategory.setParent(null);
        return ledgerCategoryDAO.update(subCategory); //update parent_id in database
    }

    public boolean demoteCategory(LedgerCategory category, LedgerCategory parent) {
        if (category == null) {
            return false;
        }
        if (parent == null) {
            return false;
        }
        if (parent.getParent() != null) {
            return false;
        }
        if (category.getId() == parent.getId()) {
            return false;
        }
        if (category.getParent() != null) {
            return false;
        }
        if(!ledgerCategoryDAO.getCategoriesByParentId(category.getId(), category.getLedger()).isEmpty()){
            return false;
        }
        if (category.getType() != parent.getType()) {
            return false;
        }
        category.setParent(parent);
        return ledgerCategoryDAO.update(category); //update parent_id in database
    }

    public boolean rename(LedgerCategory category, String newName) {
        if(category == null){
            return false;
        }
        if(newName == null || newName.isEmpty()) {
            return false;
        }
        Ledger ledger = category.getLedger();

        LedgerCategory existingCategory = ledgerCategoryDAO.getByNameAndLedger(newName, ledger);
        if(existingCategory != null && existingCategory.getId() != category.getId()) {
            return false;
        }
        category.setName(newName);
        return ledgerCategoryDAO.update(category);
    }

    public boolean deleteCategory(LedgerCategory category) {
        if(category == null){
            return false;
        }
        if(!ledgerCategoryDAO.getCategoriesByParentId(category.getId(), category.getLedger()).isEmpty()){
            return false;
        }
        Ledger ledger = category.getLedger();
        Boolean deleted = TransactionManager.getInstance().execute(() -> {
            List<Transaction> transactionsToDelete = transactionDAO.getByLedgerId(ledger.getId());
            for (Transaction tx : transactionsToDelete) {
                Account to = null;
                Account from = null;
                if(tx.getToAccount() != null){
                    to = accountDAO.getAccountById(tx.getToAccount().getId());
                }
                if(tx.getFromAccount() != null) {
                    from = accountDAO.getAccountById(tx.getFromAccount().getId());
                }
                switch (tx.getType()) {
                    case INCOME:
                        if (to != null) {
                            to.debit(tx.getAmount());
                            if (!accountDAO.update(to)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        break;
                    case EXPENSE:
                        if (from != null) {
                            from.credit(tx.getAmount());
                            if (!accountDAO.update(from)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        break;
                    case TRANSFER:
                        if (from != null) {
                            from.credit(tx.getAmount());
                            if (!accountDAO.update(from)) throw new Exception("Failed to update account during ledger deletion");
                        }
                        if (to != null) {
                            to.debit(tx.getAmount());
                            if(!accountDAO.update(to)) throw new Exception("Failed to update account during ledger deletion");
                            break;
                        }
                }
            }
            if(!ledgerCategoryDAO.delete(category)) throw new Exception("Failed to delete category");
            return true;
        });
        return deleted != null && deleted;
    }

    public boolean changeParent (LedgerCategory category, LedgerCategory newParent) {
        if (category == null) {
            return false;
        }
        if (category.getParent() == null) {
            return false;
        }

        if (newParent != null) {
            if (newParent.getParent() != null) {
                return false;
            }
            if (category.getId() == newParent.getId()) {
                return false;
            }
            if (category.getType() != newParent.getType()) {
                return false;
            }
        } else {
            return false; //cannot set parent to null with this method
        }
        category.setParent(newParent);
        return ledgerCategoryDAO.update(category);
    }

}

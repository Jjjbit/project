package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.BudgetDAO;
import com.ledger.orm.LedgerCategoryDAO;
import com.ledger.orm.TransactionDAO;

import java.math.BigDecimal;
import java.util.List;

public class LedgerCategoryController {
    private final LedgerCategoryDAO ledgerCategoryDAO;
    private final TransactionDAO transactionDAO;
    private final BudgetDAO budgetDAO;

    public LedgerCategoryController(LedgerCategoryDAO ledgerCategoryDAO,
                                    TransactionDAO transactionDAO, BudgetDAO budgetDAO) {
        this.budgetDAO = budgetDAO;
        this.transactionDAO = transactionDAO;
        this.ledgerCategoryDAO = ledgerCategoryDAO;
    }

    public List<LedgerCategory> getLedgerCategoryTreeByLedger(Ledger ledger) {
        return ledgerCategoryDAO.getTreeByLedgerId(ledger.getId()).stream()
                .filter(category -> !category.getName().equals("Claim Income"))
                .toList();
    }

    public LedgerCategory createCategory(String name, Ledger ledger, CategoryType type) {
        if(ledger == null){
            return null;
        }
        if(name == null || name.isEmpty()) {
            return null;
        }
        if(type == null){
            return null;
        }
        if(ledgerCategoryDAO.getByNameAndLedger(name, ledger) != null) {
            return null;
        }
        LedgerCategory category = new LedgerCategory(name, type, ledger);
        ledgerCategoryDAO.insert(category);

        //create budget for ledgerCategory
        for(Period period : Period.values()){
            Budget budget = new Budget(BigDecimal.ZERO, period, category, ledger);
            budgetDAO.insert(budget);
        }
        return category;
    }

    public LedgerCategory createSubCategory(String name, LedgerCategory parentCategory, Ledger ledger) {
        if(parentCategory == null){
            return null;
        }
        if(parentCategory.getParent()!=null){
            return null;
        }
        if(name == null || name.isEmpty()) {
            return null;
        }
        if (ledgerCategoryDAO.getByNameAndLedger(name, ledger) != null) {
            return null;
        }

        LedgerCategory category = new LedgerCategory(name, parentCategory.getType(), ledger);
        category.setParent(parentCategory);
        ledgerCategoryDAO.insert(category);

        //create budget for ledgerCategory
        for(Period period : Period.values()){
            Budget budget = new Budget(BigDecimal.ZERO, period, category, ledger);
            budgetDAO.insert(budget);
        }
        return category;
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

    public boolean demoteCategory(LedgerCategory category, LedgerCategory newParent) {
        if (category == null) {
            return false;
        }
        if (newParent == null) {
            return false;
        }
        if (newParent.getParent() != null) {
            return false;
        }
        if (category.getId() == newParent.getId()) {
            return false;
        }
        if (category.getParent() != null) {
            return false;
        }
        if(!ledgerCategoryDAO.getCategoriesByParentId(category.getId()).isEmpty()){
            return false;
        }
        if (category.getType() != newParent.getType()) {
            return false;
        }
        category.setParent(newParent);
        return ledgerCategoryDAO.update(category); //update parent_id in database
    }

    public boolean renameCategory(LedgerCategory category, String newName) {
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
        if(!ledgerCategoryDAO.getCategoriesByParentId(category.getId()).isEmpty()){
            return false;
        }
        List<Transaction> txs = transactionDAO.getByCategoryId(category.getId()); //get transactions from db

        //if(deleteTransaction){//delete transactions
//            for(Transaction tx : txs){
//                transactionDAO.delete(tx);
//            }
//        } else { //migrate transactions
//            if (migrateCategory == null) {
//                return false;
//            }
//            if (migrateCategory.getId() == category.getId()) {
//                return false;
//            }
//            if (migrateCategory.getType() != category.getType()) {
//                return false;
//            }
//            for (Transaction tx : txs) {
//                tx.setCategory(migrateCategory);
//                transactionDAO.update(tx);
//            }
//        }
        return ledgerCategoryDAO.delete(category);
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

package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.*;

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

    public Ledger createLedger(String name, User owner) {
        if(name == null || name.isEmpty()) {
            return null;
        }
        if(owner == null){
            return null;
        }
        if (ledgerDAO.getByNameAndOwnerId(name, owner.getId()) != null) {
            return null;
        }
        Ledger ledger = new Ledger(name, owner);
        ledgerDAO.insert(ledger); //insert to db
        List<Category> templateCategories = new ArrayList<>(categoryDAO.getCategoriesNullParent()); //get only parents
        List<LedgerCategory> allCategories = new ArrayList<>();
        for (Category template : templateCategories) {
            allCategories.addAll(copyCategoryTree(template, ledger));
        }
        //create Budget for ledger level for each Period
        for (Budget.Period period : Budget.Period.values()) {
            Budget ledgerBudget = new Budget(BigDecimal.ZERO, period, null, ledger);
            budgetDAO.insert(ledgerBudget);
        }
        //create Budget for each default category
        for (LedgerCategory cat : allCategories) {
            if (cat.getType() == CategoryType.EXPENSE) {
                for (Budget.Period period : Budget.Period.values()) {
                    Budget categoryBudget = new Budget(BigDecimal.ZERO, period, cat, ledger);
                    budgetDAO.insert(categoryBudget);
                }
            }
        }
        return ledger;
    }

    private List<LedgerCategory> copyCategoryTree(Category template, Ledger ledger) {
        List<LedgerCategory> result = new ArrayList<>();

        LedgerCategory copy = new LedgerCategory();
        copy.setName(template.getName());
        copy.setType(template.getType());
        copy.setLedger(ledger);
        result.add(copy);
        ledgerCategoryDAO.insert(copy);

        for (Category childTemplate : categoryDAO.getCategoriesByParentId(template.getId())) {
            List<LedgerCategory> childCopies = copyCategoryTree(childTemplate, ledger);
            for (LedgerCategory childCopy : childCopies) {
                childCopy.setParent(copy);
                ledgerCategoryDAO.update(childCopy);
            }
            result.addAll(childCopies);
        }
        return result;
    }

    public boolean deleteLedger(Ledger ledger) {
        //delete ledger categories (cascade delete transactions and ledger categories -> delete budgets)
        if(ledger == null){
            return false;
        }

        List<Transaction> transactionsToDelete = transactionDAO.getByLedgerId(ledger.getId());
        for (Transaction tx : transactionsToDelete) {
            Account to = tx.getToAccount();
            Account from = tx.getFromAccount();

            switch(tx.getType()) {
                case INCOME:
                    if (to != null) {
                        to.debit(tx.getAmount()); //modifica bilancio account
                        accountDAO.update(to); //update balance in db
                    }
                    break;
                case EXPENSE:
                    if (from != null) {
                        from.credit(tx.getAmount());
                        accountDAO.update(from);
                    }
                    break;
                case TRANSFER:
                    if (from != null) {
                        from.credit(tx.getAmount());
                        accountDAO.update(from);
                    }
                    if (to != null) {
                        to.debit(tx.getAmount());
                        accountDAO.update(to);
                    }
                    break;
            }
        }
        return ledgerDAO.delete(ledger);
    }

    public Ledger copyLedger(Ledger original, User user) {
        if(original == null){
            return null;
        }
        String newName = original.getName() + " Copy";
        Ledger copy = new Ledger(newName, user);
        ledgerDAO.insert(copy); // insert to db

        List<LedgerCategory> parents = new ArrayList<>(ledgerCategoryDAO.getCategoriesNullParent(original.getId()));
        List<LedgerCategory> categoryCopies = new ArrayList<>();
        for (LedgerCategory originalCat : parents) {
            categoryCopies.addAll(copyLedgerCategoryTree(originalCat, copy));
        }

        //create Budget for ledger for each Period
        for (Budget.Period period : Budget.Period.values()) {
            Budget ledgerBudget = new Budget(BigDecimal.ZERO, period, null, copy);
            budgetDAO.insert(ledgerBudget);
        }

        //create Budgets for each category
        for (LedgerCategory cat : categoryCopies) {
            if (cat.getType() == CategoryType.EXPENSE) {
                for (Budget.Period period : Budget.Period.values()) {
                    Budget categoryBudget = new Budget(BigDecimal.ZERO, period, cat, copy);
                    budgetDAO.insert(categoryBudget);
                }
            }
        }
        return copy;
    }

    private List<LedgerCategory> copyLedgerCategoryTree(LedgerCategory oldCategory, Ledger newLedger) {
        List<LedgerCategory> result = new ArrayList<>();

        LedgerCategory copy = new LedgerCategory();
        copy.setName(oldCategory.getName());
        copy.setType(oldCategory.getType());
        copy.setLedger(newLedger);
        result.add(copy);
        ledgerCategoryDAO.insert(copy);

        for(LedgerCategory child : ledgerCategoryDAO.getCategoriesByParentId(oldCategory.getId())) {
            List<LedgerCategory> childCategories = copyLedgerCategoryTree(child, newLedger);
            for (LedgerCategory childCategory : childCategories) {
                childCategory.setParent(copy);
                ledgerCategoryDAO.update(childCategory);
            }
            result.addAll(childCategories);
        }
        return result;
    }

    public boolean renameLedger(Ledger ledger, String newName, User user) {
        if(ledger == null){
            return false;
        }
        if(newName == null || newName.isEmpty()) {
            return false;
        }
        Ledger existingLedger = ledgerDAO.getByNameAndOwnerId(newName, user.getId());
        if (existingLedger != null && existingLedger.getId() != ledger.getId()) {
            return false;
        }
        ledger.setName(newName);
        return ledgerDAO.update(ledger);
    }
}
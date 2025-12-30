package com.ledger.business;

import com.ledger.domain.*;
import com.ledger.orm.*;
import com.ledger.transaction.DbTransactionManager;
import com.ledger.session.UserSession;

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

//    public Ledger createLedger(String name) {
//        if(name == null || name.isEmpty() || name.length() > 50) {
//            return null;
//        }
//        if(!UserSession.isLoggedIn()){
//            return null;
//        }
//        User owner = UserSession.getCurrentUser();
//        if (ledgerDAO.getByNameAndOwnerId(name, owner.getId()) != null) {
//            return null;
//        }
//        Ledger ledger = new Ledger(name, owner);
//        Connection connection = ConnectionManager.getInstance().getConnection();
//        try{
//            connection.setAutoCommit(false);
//            if(!ledgerDAO.insert(ledger)){
//                throw new SQLException("Failed to create ledger");
//            }
//            List<Category> templateCategories = new ArrayList<>(categoryDAO.getParentCategories()); //get only parents
//            List<LedgerCategory> allCategories = new ArrayList<>();
//            for (Category template : templateCategories) {
//                //allCategories.addAll(copyCategoryTree(template, ledger));
//                allCategories.addAll(copyCategoryTree(template, ledger, null));
//            }
//            //create Budget for ledger level for each Period
//            for (Period period : Period.values()) {
//                Budget ledgerBudget = new Budget(BigDecimal.ZERO, period, null, ledger);
//                if(!budgetDAO.insert(ledgerBudget)){
//                    throw new SQLException("Failed to create ledger budget");
//                }
//            }
//            //create Budget for each default category
//            for (LedgerCategory cat : allCategories) {
//                if (cat.getType() == CategoryType.EXPENSE) {
//                    for (Period period : Period.values()) {
//                        Budget categoryBudget = new Budget(BigDecimal.ZERO, period, cat, ledger);
//                        if(!budgetDAO.insert(categoryBudget)){
//                            throw new SQLException("Failed to create category budget");
//                        }
//                    }
//                }
//            }
//            connection.commit();
//            return ledger;
//        }catch (SQLException e){
//            System.err.println("Error creating ledger: " + e.getMessage());
//            try {
//                connection.rollback();
//            } catch (SQLException ex) {
//                System.err.println("Error during rollback: " + ex.getMessage());
//            }
//            return null;
//        }finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException e) {
//                System.err.println("Error resetting auto-commit: " + e.getMessage());
//            }
//        }
//    }
//
//    private List<LedgerCategory> copyCategoryTree(Category template, Ledger ledger, LedgerCategory parent) {
//        List<LedgerCategory> result = new ArrayList<>();
//
//        LedgerCategory copy = new LedgerCategory();
//        copy.setName(template.getName());
//        copy.setType(template.getType());
//        copy.setLedger(ledger);
//        copy.setParent(parent);
//
//        if (!ledgerCategoryDAO.insert(copy)) {
//            throw new RuntimeException("cannot insert category: " + template.getName());
//        }
//        result.add(copy);
//
//        for (Category childTemplate : categoryDAO.getCategoriesByParentId(template.getId())) {
//            List<LedgerCategory> childCopies = copyCategoryTree(childTemplate, ledger, copy);
//            result.addAll(childCopies);
//        }
//        return result;
//    }

    public Ledger createLedger(String name) {
        if(name == null || name.isEmpty() || name.length() > 50) {
            return null;
        }
        if(!UserSession.isLoggedIn()){
            return null;
        }
        User owner = UserSession.getCurrentUser();
        if (ledgerDAO.getByNameAndOwnerId(name, owner.getId()) != null) {
            return null;
        }
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

//    public boolean deleteLedger(Ledger ledger) {
//        if(ledger == null){
//            return false;
//        }
//        Connection connection = ConnectionManager.getInstance().getConnection();
//        try {
//            connection.setAutoCommit(false);
//            List<Transaction> transactionsToDelete = transactionDAO.getByLedgerId(ledger.getId());
//            for (Transaction tx : transactionsToDelete) {
//                Account to = null;
//                Account from = null;
//                if (tx.getToAccount() != null) {
//                    to = accountDAO.getAccountById(tx.getToAccount().getId());
//                }
//                if (tx.getFromAccount() != null) {
//                    from = accountDAO.getAccountById(tx.getFromAccount().getId());
//                }
//
//                switch (tx.getType()) {
//                    case INCOME:
//                        if (to != null) {
//                            to.debit(tx.getAmount());
//                            if(!accountDAO.update(to)) throw new SQLException("Failed to update account during ledger deletion");
//                        }
//                        break;
//                    case EXPENSE:
//                        if (from != null) {
//                            from.credit(tx.getAmount());
//                            if(!accountDAO.update(from)) throw new SQLException("Failed to update account during ledger deletion");
//                        }
//                        break;
//                    case TRANSFER:
//                        if (from != null) {
//                            from.credit(tx.getAmount());
//                            if(!accountDAO.update(from)) throw new SQLException("Failed to update account during ledger deletion");
//
//                        }
//                        if (to != null) {
//                            to.debit(tx.getAmount());
//                            if(!accountDAO.update(to)) throw new SQLException("Failed to update account during ledger deletion");
//
//                        }
//                        break;
//                }
//            }
//            connection.commit();
//            if(!ledgerDAO.delete(ledger)){
//                throw new SQLException("Failed to delete ledger");
//            }
//            return true;
//        }catch (SQLException e){
//            System.err.println("Error deleting ledger: " + e.getMessage());
//            try {
//                connection.rollback();
//            } catch (SQLException ex) {
//                System.err.println("Error during rollback: " + ex.getMessage());
//            }
//            return false;
//        }finally {
//            try {
//                connection.setAutoCommit(true);
//            } catch (SQLException e) {
//                System.err.println("Error resetting auto-commit: " + e.getMessage());
//            }
//        }
//    }

    public boolean deleteLedger(Ledger ledger) {
        if(ledger == null){
            return false;
        }
        Boolean deleted = DbTransactionManager.getInstance().execute(() -> {
            List<Transaction> transactionsToDelete = transactionDAO.getByLedgerId(ledger.getId());
            for (Transaction tx : transactionsToDelete) {
                Account to = null;
                Account from = null;
                if (tx.getToAccount() != null) {
                    to = accountDAO.getAccountById(tx.getToAccount().getId());
                }
                if (tx.getFromAccount() != null) {
                    from = accountDAO.getAccountById(tx.getFromAccount().getId());
                }
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
        if(ledger == null){
            return false;
        }
        if(newName == null || newName.isEmpty()) {
            return false;
        }
        if(!UserSession.isLoggedIn()){
            return false;
        }
        User user = UserSession.getCurrentUser();
        Ledger existingLedger = ledgerDAO.getByNameAndOwnerId(newName, user.getId());
        if (existingLedger != null && existingLedger.getId() != ledger.getId()) {
            return false;
        }
        ledger.setName(newName);
        return ledgerDAO.update(ledger);
    }
}
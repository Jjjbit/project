package com.ledger.business;

import com.ledger.domain.Budget;
import com.ledger.domain.CategoryType;
import com.ledger.domain.LedgerCategory;
import com.ledger.domain.User;
import com.ledger.orm.BudgetDAO;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class BudgetController {
    private BudgetDAO budgetDAO;


    public BudgetController(BudgetDAO budgetDAO) {
        this.budgetDAO = budgetDAO;
    }

    public Budget createBudget(User user, BigDecimal amount, LedgerCategory category,
                               Budget.Period period) throws SQLException {

        if (category != null) {

            if (category.getType().equals(CategoryType.INCOME)) {
                return null; // Cannot create budget for income category
            }

            if (budgetDAO.getActiveBudgetsByCategoryId(category.getId(), period)!=null) {
                return null; // Budget for this category and period already exists
            }
        } else {
            if (budgetDAO.getActiveBudgetsByUserId(user.getId(), period)!=null) {
                return null; // Budget for this user and period already exists
            }
        }

        Budget budget = new Budget(amount, period, category, user);

        if (category != null) {
            category.getBudgets().add(budget);
        }
        user.getBudgets().add(budget);

        budgetDAO.insert(budget);
        return budget;
    }

    public boolean editBudget(Budget budget, BigDecimal newAmount) throws SQLException {
        if (budget == null) {
            return false;
        }
        budget.setAmount(newAmount);
        return budgetDAO.update(budget);
    }

    public boolean mergeBudgets(Budget targetBudget) throws SQLException {

        if (!targetBudget.isActive(LocalDate.now())) {
            return false;
        }

        User user=targetBudget.getOwner();
        if (targetBudget.getCategory() == null) { //merge category budget into user budget
            //List<Ledger> ledgers = ledgerDAO.getLedgersByUserId(user.getId());
            List<Budget> sourceBudgets = user.getLedgers().stream()
                    .flatMap(l -> l.getCategories().stream())
                    .flatMap(c -> c.getBudgets().stream())
                    .filter(b -> b.getCategory().getParent() == null) //first-level categories only
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //same period
                    .filter(b -> b.isActive(LocalDate.now())) //active budgets only
                    .toList();

            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));

        } else { //merge subcategory budget into category budget
            if (targetBudget.getCategory().getParent() != null) {
                throw new IllegalArgumentException("Target budget must be for a first-level category");
            }
            List<Budget> sourceBudgets = targetBudget.getCategory().getChildren().stream() //tutte le subcategorie di targetBudget
                    .flatMap(c -> c.getBudgets().stream()) //tutti i budget di tutte le subcategorie di targetBudget
                    .filter(b -> b.getPeriod() == targetBudget.getPeriod()) //stesso periodo
                    .filter(b -> b.isActive(LocalDate.now())) //solo budget attivi
                    .toList();//tutti i budget di tutte le subcategorie di targetBudget non attivi in periodo di targetBudget
            BigDecimal mergedAmount = sourceBudgets.stream()
                    .map(Budget::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            targetBudget.setAmount(targetBudget.getAmount().add(mergedAmount));
        }

        return budgetDAO.update(targetBudget);
    }

    public boolean deleteBudget(Budget budget) throws SQLException {
        User user = budget.getOwner();

        if (budget.getCategory() != null) {
            LedgerCategory category = budget.getCategory();
            category.getBudgets().remove(budget);
            budget.setCategory(null);
        }
        user.getBudgets().remove(budget);


        return budgetDAO.delete(budget);
    }




}
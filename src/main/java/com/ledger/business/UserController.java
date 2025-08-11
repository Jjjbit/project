package com.ledger.business;

import com.ledger.domain.PasswordUtils;
import com.ledger.domain.User;
import com.ledger.orm.CategoryComponentDAO;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

public class UserController {
    private UserDAO userDAO;
    private CategoryComponentDAO categoryComponentDAO;

    public UserController( UserDAO userDAO, CategoryComponentDAO categoryComponentDAO) {
        this.userDAO = userDAO;
        this.categoryComponentDAO = categoryComponentDAO;
    }

    public String login(String username, String password) {
        User user = userDAO.findByUsername(username);
        if (user == null) return "User not found";
        if (!PasswordUtils.verify(password, user.getPasswordHash())) return "Password incorrect";
        return "Login successful";
    }
    public String register(String username, String password) {
        if(userDAO.findByUsername(username) != null) return "Username already exists";
        User user = new User(username, PasswordUtils.hash(password));
        userDAO.save(user);
        return "Registration successful";
    }

    @Transactional
    public void updateUserInfo(Long userId, String newUsername, String newPassword) {
        User user = userDAO.findById(userId);
        if (user != null) {
            if (newUsername != null && !newUsername.isEmpty()) {
                user.setUsername(newUsername);
            }
            if (newPassword != null && !newPassword.isEmpty()) {
                user.setPassword(PasswordUtils.hash(newPassword));
            }
            userDAO.update(user);
        }
    }
    public User getUserById(Long userId) {
        return userDAO.findById(userId);
    }

    /*public List<Account> getAccountsByUserId(Long userId) {
        return userDAO.findById(userId).getAccounts();
    }

    public List<Ledger> getLedgersByUserId(Long userId) {
        return userDAO.findById(userId).getLedgers();
    }

    public BigDecimal getTotalBudgetByUserId(Long userId, Budget.BudgetPeriod period) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        BudgetManager budgetManager = new BudgetManager(user.getBudgets());
        return budgetManager.getUserTotalBudget(user, period);
    }

    public BigDecimal getCategoryBudgetsByUserId(Long userId, Long categoryId, Budget.BudgetPeriod period) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        CategoryComponent category = categoryComponentDAO.findById(categoryId);
        BudgetManager budgetManager = new BudgetManager(user.getBudgets());
        return budgetManager.getCategoryBudgets(user, period, category);
    }*/

    public BigDecimal getTotalNetAssetByUserId(Long userId) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user.getNetAssets();
    }

    public BigDecimal getTotalAssetsByUserId(Long userId) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user.getTotalAssets();
    }
    public BigDecimal getTotalLiabilitiesByUserId(Long userId) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return user.getTotalLiabilities();
    }

}

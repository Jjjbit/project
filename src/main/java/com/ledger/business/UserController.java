package com.ledger.business;

import com.ledger.domain.PasswordUtils;
import com.ledger.domain.User;
import com.ledger.orm.UserDAO;
import jakarta.transaction.Transactional;

public class UserController {
    private UserDAO userDAO;

    public UserController( UserDAO userDAO) {
        this.userDAO = userDAO;
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

}

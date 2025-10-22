package com.ledger.business;

import com.ledger.domain.Ledger;
import com.ledger.domain.PasswordUtils;
import com.ledger.domain.User;
import com.ledger.orm.UserDAO;

import java.sql.SQLException;

public class LoginUserController {
    private UserDAO userDAO;

    public LoginUserController( UserDAO userDAO){
        this.userDAO = userDAO;
    }

    public User login(String username, String password) throws SQLException {
        User user = userDAO.getUserByUsername(username);
        if (user == null) return null;
        if (PasswordUtils.verify(password, user.getPassword())) {
            return user;
        }
        return null;
    }

    public boolean register(String username, String password) throws SQLException {
        if (userDAO.getUserByUsername(username) == null) {
            User user = new User(username, PasswordUtils.hash(password));
            Ledger ledger = new Ledger("Default Ledger", user);

            return userDAO.register(user);
        }
        return false;
    }
}

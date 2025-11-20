package com.ledger.business;

import com.ledger.domain.PasswordUtils;
import com.ledger.domain.User;
import com.ledger.orm.UserDAO;

import java.sql.SQLException;

public class UserController {
    private final UserDAO userDAO;
    private User currentUser;

    public UserController( UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String password) {
        try{
        User user = userDAO.getUserByUsername(username);
        if (user != null && PasswordUtils.verify(password, user.getPassword())) {
            currentUser = user;
            return user;
        }
        }catch (SQLException e){
            System.err.println("SQL Exception during login: " + e.getMessage());
        }
        return null;
    }

    public boolean register(String username, String password) {
        if (password.isEmpty()) {
            return false;
        }
        if(username.isEmpty()) {
            return false;
        }
        if(password.length() < 6) {
            return false;
        }

        try {
            if (userDAO.getUserByUsername(username) == null) {
                User user = new User(username, PasswordUtils.hash(password));
                return userDAO.register(user);
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during registration: " + e.getMessage());
        }
        return false;
    }

     public boolean updateUsername(User user, String newUsername) {
        if(currentUser == null || currentUser.getId() != user.getId()){
            return false;
        }
        try {
            if (userDAO.getUserByUsername(newUsername) != null) {
                return user.getUsername().equals(newUsername);
            }

            user.setUsername(newUsername);
            return userDAO.updateUser(user);
        }catch (SQLException e){
            System.err.println("SQL Exception during username update: " + e.getMessage());
            return false;
        }
     }

    public boolean updatePassword(User user, String newPassword) {
        try {
            if (user != null) {
                String hashedPassword = PasswordUtils.hash(newPassword);
                user.setPassword(hashedPassword);
                return userDAO.updateUser(user);
            }
        }catch (SQLException e){
            System.err.println("SQL Exception during password update: " + e.getMessage());
        }
        return false;
    }

    public User getCurrentUser(){
        return currentUser;
    }

    public void logout() {
        currentUser = null;
    }

}

package com.ledger.business;

import com.ledger.domain.PasswordUtils;
import com.ledger.domain.User;
import com.ledger.orm.UserDAO;

public class UserController {
    private final UserDAO userDAO;
    private User currentUser;

    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String password) {
        User user = userDAO.getUserByUsername(username);
        if (user != null && PasswordUtils.verify(password, user.getPassword())) {
            currentUser = user;
            return user;
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


        if (userDAO.getUserByUsername(username) == null) {
            User user = new User(username, PasswordUtils.hash(password));
            return userDAO.register(user);
        }
        return false;
    }

     public boolean updateUsername(String newUsername) {
        if(currentUser == null ){
            return false;
        }
        if(newUsername.isEmpty()) {
            return false;
        }

        if (userDAO.getUserByUsername(newUsername) != null) {
            return currentUser.getUsername().equals(newUsername);
        }
        currentUser.setUsername(newUsername);
        return userDAO.updateUser(currentUser);
     }

    public boolean updatePassword(String newPassword) {
        if(currentUser == null ){
            return false;
        }
        if(newPassword.isEmpty()) {
            return false;
        }
        if(newPassword.length() < 6) {
            return false;
        }
        String hashedPassword = PasswordUtils.hash(newPassword);
        currentUser.setPassword(hashedPassword);
        return userDAO.updateUser(currentUser);
    }

    public User getCurrentUser(){
        return currentUser;
    }

    public void logout() {
        currentUser = null;
    }

}

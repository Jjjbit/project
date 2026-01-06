package com.ledger.BusinessLogic;

import com.ledger.Util.PasswordUtils;
import com.ledger.DomainModel.User;
import com.ledger.ORM.UserDAO;
import com.ledger.Session.UserSession;

public class UserController {
    private final UserDAO userDAO;

    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String password) {
        User user = userDAO.getUserByUsername(username);
        if (user != null && PasswordUtils.verify(password, user.getPassword())) {
            UserSession.getInstance().login(user);
            return user;
        }
        return null;
    }

    public boolean register(String username, String password) {
        if(username == null || password == null) return false;
        if (password.isEmpty() || username.isEmpty()) return false;
        if(password.length() < 6 || password.length() > 50) return false;
        if(username.length() > 50) return false;
        if (userDAO.getUserByUsername(username) == null) {
            User user = new User(username, PasswordUtils.hash(password));
            return userDAO.register(user);
        }
        return false;
    }

     public boolean updateUsername(String newUsername) {
        if(!UserSession.getInstance().isLoggedIn()) return false;
        User currentUser = UserSession.getInstance().getCurrentUser();
        if(newUsername.isEmpty()) return false;
        if(newUsername.length() > 50) return false;
        if (userDAO.getUserByUsername(newUsername) != null) {
            return currentUser.getUsername().equals(newUsername);
        }
        currentUser.setUsername(newUsername);
        return userDAO.update(currentUser);
     }

    public boolean updatePassword(String newPassword) {
        if(!UserSession.getInstance().isLoggedIn()) return false;
        User currentUser = UserSession.getInstance().getCurrentUser();
        if(newPassword.isEmpty()) return false;
        if(newPassword.length() < 6 || newPassword.length() > 50) return false;
        String hashedPassword = PasswordUtils.hash(newPassword);
        currentUser.setPassword(hashedPassword);
        return userDAO.update(currentUser);
    }

    public User getCurrentUser(){
        return UserSession.getInstance().getCurrentUser();
    }

    public void logout() {
        UserSession.getInstance().logout();
    }

}

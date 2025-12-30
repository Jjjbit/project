package com.ledger.session;

import com.ledger.domain.User;

public final class UserSession { //prevent extension
    private User currentUser;
    private static UserSession instance;

    private UserSession() {} // Prevent instantiation
    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void login(User user) {
        currentUser = user;
    }

    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }
}

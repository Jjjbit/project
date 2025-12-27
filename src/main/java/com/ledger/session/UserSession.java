package com.ledger.session;

import com.ledger.domain.User;

public final class UserSession { //prevent extension
    private static User currentUser;

    private UserSession() {} // Prevent instantiation

    public static void login(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}

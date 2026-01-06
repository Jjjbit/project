package com.ledger.CLI;

import com.ledger.BusinessLogic.ReportController;
import com.ledger.BusinessLogic.UserController;
import com.ledger.DomainModel.User;

import java.math.BigDecimal;
import java.util.Scanner;

public class UserCLI {
    private final UserController userController;
    private final ReportController reportController;
    private final Scanner scanner = new Scanner(System.in);

    public UserCLI(UserController userController, ReportController reportController) {
        this.reportController = reportController;
        this.userController = userController;
    }

    public void register() {
        System.out.println("\n=== User Registration ===");

        System.out.print("Enter Username: ");
        String username = inputUsername();
        System.out.print("Enter Password: ");
        String password = inputPassword();

        boolean success= userController.register(username, password);
        if(!success){
            System.out.println(" Registration failed: Username already taken.");
            return;
        }
        System.out.println(" Registration successful! You can now login with your credentials.");
        login();
    }

    public void login() {
        System.out.println("\n=== User Login ===");

        System.out.print("\nEnter Username: ");
        String username = scanner.nextLine().trim();

        System.out.print("\nEnter Password: ");
        String password = scanner.nextLine().trim();

        User user = userController.login(username, password);
        if (user == null) {
            System.out.println(" ! Login failed: Invalid username or password");
            return;
        }
        System.out.println("Login successful! Welcome back, " + user.getUsername());
    }

    public void logout() {
        userController.logout();
        System.out.println("Logged out successfully!");
    }

    public void showCurrentUser() {

        User user = userController.getCurrentUser();

        BigDecimal totalAssets = reportController.getTotalAssets(user);

        System.out.println("\n=== Current User ===");
        System.out.print("\nUsername: " + user.getUsername());
        System.out.print(" | Total Worth: " + totalAssets);
    }


    public void changePassword() {
        System.out.println("\n=== Change Password ===");

        System.out.print("Enter new password: ");
        String newPassword = inputPassword();

        boolean success = userController.updatePassword(newPassword);
        if (!success) {
            System.out.println("Password change failed.");
            return;
        }
        System.out.println("Password changed successfully.");
    }

    public void changeUsername() {
        System.out.println("\n=== Change Username ===");
        System.out.println("Current username: " + userController.getCurrentUser().getUsername());
        System.out.print("Enter new username: ");
        String newUsername = inputUsername();

        boolean success = userController.updateUsername(newUsername);
        if (!success) {
            System.out.println("Username change failed.");
            return;
        }
        System.out.println("Username changed successfully to " + newUsername);
    }

    public boolean isUserLoggedIn(){
        return userController.getCurrentUser() != null;
    }


    // private methods
    private String inputUsername() {
        String username = scanner.nextLine().trim();

        if (username.isEmpty()) {
            System.out.println("Username cannot be empty!");
            return inputUsername();
        }
        return username;
    }

    private String inputPassword() {
        String password = scanner.nextLine().trim();

        if (password.isEmpty()) {
            System.out.println("Password cannot be empty!");
            System.out.print("Enter Password: ");
            return inputPassword();
        }

        if (password.length() < 6) {
            System.out.println("Password must be at least 6 characters long!");
            return inputPassword();
        }

        System.out.print("Confirm password: ");
        String confirmPassword = scanner.nextLine().trim();

        if (!password.equals(confirmPassword)) {
            System.out.println("Passwords do not match!");
            return inputPassword();
        }

        return password;
    }

}

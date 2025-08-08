package com.ledger.orm;

import com.ledger.domain.User;

public interface UserDAO {
    void save(User user); // Save a new user to the database
    User findById(Long id); // Find a user by ID
    User findByUsername(String username); // Find a user by username
    void update(User user); // Update an existing user
    void delete(Long id); // Delete a user by ID
}

package com.example.settlement.service;

import com.example.settlement.dto.request.RegisterUserRequest;
import com.example.settlement.dto.response.UserResponse;
import com.example.settlement.entity.User;

/** Application user management. */
public interface UserService {

    UserResponse register(RegisterUserRequest request);

    /** Creates a user with an already-known plain password; used by the sample data seeder. */
    User createUser(String username, String email, String rawPassword, String fullName, com.example.settlement.enums.Role role);

    User requireUser(String username);

    java.util.List<UserResponse> findAll();
}

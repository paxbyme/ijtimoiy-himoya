package com.manager.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.manager.dto.UserDto;
import com.manager.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto verifyToken(String idToken) throws Exception {
        FirebaseToken firebaseToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
        String uid = firebaseToken.getUid();
        UserDto user = userRepository.findById(uid);
        if (user == null) {
            throw new RuntimeException("User not found in database");
        }
        return user;
    }
}


package com.splitsmart.backend.service;

import com.splitsmart.backend.dto.RegisterRequest;
import com.splitsmart.backend.entity.User;
import com.splitsmart.backend.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    CONFLICT, "Email is already registered");
        }

        if (userRepository.existsByUscId(request.uscId())) {
            throw new ResponseStatusException(
                    CONFLICT, "USC ID is already registered");
        }

        User user = new User(
                request.name().trim(),
                email,
                request.uscId(),
                passwordEncoder.encode(request.password())
        );

        return userRepository.save(user).getId();
    }
}

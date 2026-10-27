package org.soa8.freight.auth.controller;

import org.soa8.freight.auth.model.User;
import org.soa8.freight.auth.repository.UserRepository;
import org.soa8.freight.auth.security.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public static class LoginRequest {
        public String username;
        public String password;
    }

    public static class AuthResponse {
        public String token;
        public String tokenType = "Bearer";
        public long expiresInSeconds = 3600;
        public String userId;
        public String username;
        public Set<String> roles;

        public AuthResponse(String token, String userId, String username, Set<String> roles) {
            this.token = token;
            this.userId = userId;
            this.username = username;
            this.roles = roles;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userRepository.findByUsername(request.username);
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password, userOpt.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_CREDENTIALS",
                    "message", "Invalid username or password"
            ));
        }

        User user = userOpt.get();
        String token = jwtProvider.generateToken(user.getId(), user.getUsername(), user.getRoles());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getUsername(), user.getRoles()));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "Missing or malformed Authorization header"
            ));
        }

        try {
            String token = authHeader.substring(7);
            var claims = jwtProvider.validateAndGetClaims(token);
            return ResponseEntity.ok(Map.of(
                    "userId", claims.getSubject(),
                    "username", claims.get("username", String.class),
                    "roles", claims.get("roles", List.class)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_TOKEN",
                    "message", "JWT token expired or signature invalid"
            ));
        }
    }
}

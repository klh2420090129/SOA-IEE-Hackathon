package org.soa8.freight.auth.config;

import org.soa8.freight.auth.model.User;
import org.soa8.freight.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("shipper@soa8.org").isEmpty()) {
                userRepository.save(new User(
                        "usr-shipper-001",
                        "shipper@soa8.org",
                        passwordEncoder.encode("ShipperPass123!"),
                        Set.of("ROLE_SHIPPER")
                ));
            }
            if (userRepository.findByUsername("planner@soa8.org").isEmpty()) {
                userRepository.save(new User(
                        "usr-planner-001",
                        "planner@soa8.org",
                        passwordEncoder.encode("PlannerPass123!"),
                        Set.of("ROLE_LOAD_PLANNER")
                ));
            }
            if (userRepository.findByUsername("admin@soa8.org").isEmpty()) {
                userRepository.save(new User(
                        "usr-admin-001",
                        "admin@soa8.org",
                        passwordEncoder.encode("AdminPass123!"),
                        Set.of("ROLE_ADMIN")
                ));
            }
        };
    }
}

package org.example.bootstrap;

import org.example.domain.User;
import org.example.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        User u = userRepository.findByUsername("admin").orElse(null);
        if (u == null) {
            u = new User();
            u.setUsername("admin");
            u.setNickname("管理员");
        }
        u.setPasswordHash(passwordEncoder.encode("admin123456"));
        u.setRole("admin");
        u.setStatus((short) 1);
        userRepository.save(u);
    }
}


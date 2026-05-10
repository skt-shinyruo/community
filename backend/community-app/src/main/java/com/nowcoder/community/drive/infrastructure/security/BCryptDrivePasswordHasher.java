package com.nowcoder.community.drive.infrastructure.security;

import com.nowcoder.community.drive.application.port.DrivePasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptDrivePasswordHasher implements DrivePasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword == null ? "" : rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }
        try {
            return encoder.matches(rawPassword == null ? "" : rawPassword, passwordHash);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}

package com.nowcoder.community.drive.application.port;

public interface DrivePasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}

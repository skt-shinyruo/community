package com.nowcoder.community.user.domain.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsernamePolicyDomainServiceTest {

    private final UsernamePolicyDomainService policy = new UsernamePolicyDomainService();

    @Test
    void acceptsVisibleUnicodeAndAsciiUsernames() {
        assertThat(policy.requireValid("  c\u0153ur  ")).isEqualTo("c\u0153ur");
        assertThat(policy.isSafe("alice_01")).isTrue();
    }

    @Test
    void rejectsDefaultIgnorableAndControlCharacters() {
        assertThatThrownBy(() -> policy.requireValid("alice\u200D"))
                .isInstanceOf(RuntimeException.class);
        assertThat(policy.isSafe("alice\n")).isFalse();
    }

    @Test
    void registrationOwnerUsesTheSameUsernameBoundary() {
        UserRegistrationDomainService registration = new UserRegistrationDomainService(
                java.time.Clock.systemUTC(),
                new PasswordPolicyDomainService(),
                policy
        );

        assertThatThrownBy(() -> registration.requireValidRegistration(
                "alice\u200D", "secret12", "alice@example.com"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void operationsAuditShouldCoverExplicitUnsafeCodePointBoundaries() throws IOException {
        String operations = Files.readString(Path.of("..", "..", "docs", "handbook", "operations.md")
                .toAbsolutePath()
                .normalize());
        int[] boundaryCodePoints = {
                0x034F,
                0x115F, 0x1160,
                0x17B4, 0x17B5,
                0x180B, 0x180F,
                0x3164,
                0xFE00, 0xFE0F,
                0xFFA0,
                0x1BCA0, 0x1BCA3,
                0x1D173, 0x1D17A,
                0xE0000, 0xE0FFF
        };

        for (int codePoint : boundaryCodePoints) {
            String character = new String(Character.toChars(codePoint));
            String utf8Hex = HexFormat.of().withUpperCase()
                    .formatHex(character.getBytes(StandardCharsets.UTF_8));

            assertThat(policy.isSafe("a" + character + "b"))
                    .as("U+%04X must remain rejected", codePoint)
                    .isFalse();
            assertThat(operations)
                    .as("operations audit must include U+%04X as %s", codePoint, utf8Hex)
                    .contains("'" + utf8Hex + "'");
        }
    }
}

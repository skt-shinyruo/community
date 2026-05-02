package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.config.RegistrationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LogMailAdapterTest {

    @Test
    void sendPasswordResetMailShouldNotLogResetLinkOrToken(CapturedOutput output) {
        LogMailAdapter adapter = new LogMailAdapter(new RegistrationProperties());

        adapter.sendPasswordResetMail(
                "alice@example.com",
                "https://community.example/#/auth/password/reset?token=reset-token-123"
        );

        assertThat(output.getAll())
                .contains("[mail][password-reset][disabled]")
                .contains("alice@example.com")
                .contains("重置密码")
                .doesNotContain("https://community.example")
                .doesNotContain("/#/auth/password/reset")
                .doesNotContain("reset-token-123")
                .doesNotContain("token=");
    }
}

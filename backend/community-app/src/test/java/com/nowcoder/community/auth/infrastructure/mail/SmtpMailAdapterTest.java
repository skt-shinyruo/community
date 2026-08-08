package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.config.RegistrationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpMailAdapterTest {

    @Test
    void registrationMailShouldCarryStableMessageIdFromDeliveryReference() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getMail().setFrom("no-reply@community.local");
        SmtpMailAdapter adapter = new SmtpMailAdapter(sender, properties);

        String reference = "A".repeat(32);
        adapter.sendRegistrationCodeMail("alice@example.com", "123456", reference);

        verify(sender).send(message);
        assertThat(message.getHeader("Message-ID", null))
                .isEqualTo("<registration-code." + reference + "@community.invalid>");
    }

    @Test
    void passwordResetMailShouldCarryStableMessageIdFromDeliveryReference() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        RegistrationProperties properties = new RegistrationProperties();
        properties.getMail().setFrom("no-reply@community.local");
        SmtpMailAdapter adapter = new SmtpMailAdapter(sender, properties);

        String reference = "A".repeat(43);
        adapter.sendPasswordResetMail(
                "alice@example.com",
                "https://community.example/#/auth/password/reset?token=opaque-token",
                reference
        );

        verify(sender).send(message);
        assertThat(message.getHeader("Message-ID", null))
                .isEqualTo("<password-reset." + reference + "@community.invalid>");
    }
}

package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.config.RegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "auth.registration.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(LogMailAdapter.class);

    private final RegistrationProperties properties;

    public LogMailAdapter(RegistrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendRegistrationCodeMail(String toEmail, String code) {
        log.info("[mail][registration-code][disabled] to={}, subject={}, code={}",
                toEmail,
                properties.getMail().getSubject(),
                code
        );
    }

    @Override
    public void sendPasswordResetMail(String toEmail, String resetLink) {
        log.info("[mail][password-reset][disabled] to={}, subject={}",
                toEmail,
                "重置密码"
        );
    }
}

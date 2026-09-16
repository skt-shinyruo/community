package com.nowcoder.community.auth.infrastructure.mail;

import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.config.RegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import static com.nowcoder.community.auth.domain.service.EmailMasking.maskEmail;

@Service
@ConditionalOnProperty(name = "auth.registration.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogMailAdapter implements MailPort {

    private static final Logger log = LoggerFactory.getLogger(LogMailAdapter.class);

    private final RegistrationProperties properties;

    public LogMailAdapter(RegistrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendRegistrationCodeMail(String toEmail, String code, String deliveryReference) {
        log.info("[mail][registration-code][disabled] to={}, subject={}",
                maskEmail(toEmail),
                properties.getMail().getSubject()
        );
    }

    @Override
    public void sendPasswordResetMail(String toEmail, String resetLink, String deliveryReference) {
        log.info("[mail][password-reset][disabled] to={}, subject={}",
                maskEmail(toEmail),
                "重置密码"
        );
    }
}

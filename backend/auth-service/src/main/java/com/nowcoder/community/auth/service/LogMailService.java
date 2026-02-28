package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "auth.registration.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LogMailService.class);

    private final RegistrationProperties properties;

    public LogMailService(RegistrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendActivationMail(String toEmail, String activationLink) {
        // 默认降级行为：不依赖 SMTP，避免本地/CI 被邮箱配置阻塞
        log.info("[mail][activation][disabled] to={}, subject={}, link={}",
                toEmail,
                properties.getMail().getSubject(),
                activationLink
        );
    }

    @Override
    public void sendPasswordResetMail(String toEmail, String resetLink) {
        log.info("[mail][password-reset][disabled] to={}, subject={}, link={}",
                toEmail,
                "重置密码",
                resetLink
        );
    }
}

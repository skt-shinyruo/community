package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "auth.registration.mail.enabled", havingValue = "true")
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;
    private final RegistrationProperties properties;

    public SmtpMailService(JavaMailSender mailSender, RegistrationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendActivationMail(String toEmail, String activationLink) {
        if (!StringUtils.hasText(toEmail)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
        if (!StringUtils.hasText(activationLink)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "activationLink 不能为空");
        }

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(toEmail);
            helper.setSubject(properties.getMail().getSubject());
            helper.setText(buildHtml(activationLink), true);
            mailSender.send(mime);
        } catch (MessagingException | MailException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "发送激活邮件失败");
        }
    }

    @Override
    public void sendPasswordResetMail(String toEmail, String resetLink) {
        if (!StringUtils.hasText(toEmail)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
        if (!StringUtils.hasText(resetLink)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "resetLink 不能为空");
        }

        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(properties.getMail().getFrom());
            helper.setTo(toEmail);
            helper.setSubject("重置密码");
            helper.setText(buildResetHtml(resetLink), true);
            mailSender.send(mime);
        } catch (MessagingException | MailException e) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "发送重置密码邮件失败");
        }
    }

    private String buildHtml(String activationLink) {
        return """
                <div>
                  <p>欢迎注册社区账号，请点击下面的链接完成激活：</p>
                  <p><a href="%s" target="_blank" rel="noreferrer">%s</a></p>
                  <p>如果不是本人操作，请忽略此邮件。</p>
                </div>
                """.formatted(activationLink, activationLink);
    }

    private String buildResetHtml(String resetLink) {
        return """
                <div>
                  <p>你正在进行密码重置操作，请点击下面的链接继续：</p>
                  <p><a href="%s" target="_blank" rel="noreferrer">%s</a></p>
                  <p>如果不是本人操作，请忽略此邮件。</p>
                </div>
                """.formatted(resetLink, resetLink);
    }
}

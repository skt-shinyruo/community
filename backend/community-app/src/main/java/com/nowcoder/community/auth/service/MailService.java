package com.nowcoder.community.auth.service;

public interface MailService {

    void sendRegistrationCodeMail(String toEmail, String code);

    void sendPasswordResetMail(String toEmail, String resetLink);
}

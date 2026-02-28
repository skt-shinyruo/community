package com.nowcoder.community.auth.service;

public interface MailService {

    void sendActivationMail(String toEmail, String activationLink);

    void sendPasswordResetMail(String toEmail, String resetLink);
}

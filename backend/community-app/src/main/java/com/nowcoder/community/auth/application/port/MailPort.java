package com.nowcoder.community.auth.application.port;

public interface MailPort {

    void sendRegistrationCodeMail(String toEmail, String code, String deliveryReference);

    void sendPasswordResetMail(String toEmail, String resetLink, String deliveryReference);
}

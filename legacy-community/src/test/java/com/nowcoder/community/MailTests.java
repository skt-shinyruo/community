package com.nowcoder.community;

import com.nowcoder.community.util.MailClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
@Disabled("需要可用的 SMTP 配置, 迁移期默认禁用")
public class MailTests {

    @Autowired
    private MailClient mailClient;

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    public void testTextMail(){
        mailClient.sendMail("1654388696@qq.com", "java mail", "welcome");

    }

    @Test
    public void testHtmlMail(){

        Context context = new Context();
        context.setVariable("username", "feng");

        String content = templateEngine.process("/mail/demo", context);

        mailClient.sendMail("1654388696@qq.com", "html mail", content);


    }



}

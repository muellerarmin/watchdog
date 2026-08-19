package com.example.dockerwatchdog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
class SmtpProxyConfigurer {
    SmtpProxyConfigurer(
            JavaMailSender mailSender,
            @Value("${SMTP_SOCKS_HOST:}") String socksHost,
            @Value("${SMTP_SOCKS_PORT:1080}") int socksPort) {
        if (!socksHost.isBlank() && mailSender instanceof JavaMailSenderImpl sender) {
            sender.getJavaMailProperties().put("mail.smtp.socks.host", socksHost);
            sender.getJavaMailProperties().put("mail.smtp.socks.port", Integer.toString(socksPort));
        }
    }
}

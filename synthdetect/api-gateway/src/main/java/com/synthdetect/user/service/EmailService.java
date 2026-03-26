package com.synthdetect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:noreply@synthdetect.ai}")
    private String fromAddress;

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String link = baseUrl + "/auth/verify-email?token=" + token;
        String subject = "Verify your SynthDetect account";
        String body = """
                Welcome to SynthDetect!

                Please verify your email address by clicking the link below:
                %s

                This link expires in 24 hours.

                If you did not create an account, please ignore this email.

                — The SynthDetect Team
                """.formatted(link);

        sendEmail(toEmail, subject, body);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = baseUrl + "/auth/reset-password?token=" + token;
        String subject = "Reset your SynthDetect password";
        String body = """
                We received a request to reset your SynthDetect password.

                Click the link below to set a new password:
                %s

                This link expires in 1 hour.

                If you did not request a password reset, please ignore this email.
                Your password will not be changed.

                — The SynthDetect Team
                """.formatted(link);

        sendEmail(toEmail, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Email sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={} subject={} error={}", to, subject, e.getMessage());
        }
    }
}

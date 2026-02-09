package task_manager_api.service.notification;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailSenderUsername;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String email, String link) {
        try {
            String subject = "Task Handler - Email Verification";
            String body = "Click the following link to verify your account:\n" + link;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailSenderUsername);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            System.out.println("Email Sent to: " + email);

        } catch (Exception e) {
            System.err.println("Failed to send verification email: " + e.getMessage());
        }
    }
}

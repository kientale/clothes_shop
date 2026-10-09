package lemonadex.project.clothes.features.mail.service;

import jakarta.mail.internet.InternetAddress;
import lemonadex.project.clothes.features.mail.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends customer emails off the request thread and only once the surrounding transaction has committed,
 * so a rolled-back order or reset never mails anyone and a slow SMTP server never slows a request.
 * Delivery is best effort: failures are logged, never thrown back to the caller.
 */
@Slf4j
@Service
public class MailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final MailProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MailService(ObjectProvider<JavaMailSender> sender, MailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    /** Public shop address for links in emails. */
    public String storefront() {
        return properties.storefront();
    }

    public void send(String to, String subject, String html) {
        Runnable task = () -> executor.execute(() -> deliver(to, subject, html));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void deliver(String to, String subject, String html) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null || mail instanceof JavaMailSenderImpl impl && (impl.getHost() == null || impl.getHost().isBlank())) {
            // The body may hold a reset link, so it is printed only for a local shop (development).
            if (properties.storefront().contains("://localhost") || properties.storefront().contains("://127.0.0.1"))
                log.info("Email not sent (MAIL_HOST is not set) to={} subject={}\n{}", to, subject, html);
            else
                log.warn("Email not sent (MAIL_HOST is not set) to={} subject={}", to, subject);
            return;
        }
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.from(), properties.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mail.send(message);
        } catch (Exception ex) {
            log.warn("Email to {} failed: {}", to, ex.getMessage());
        }
    }
}

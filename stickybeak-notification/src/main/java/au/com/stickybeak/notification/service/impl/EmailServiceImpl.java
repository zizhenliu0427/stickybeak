package au.com.stickybeak.notification.service.impl;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.notification.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendOrderConfirmation(OrderPaidEvent event) {
        String recipient = StringUtils.hasText(event.getEmail()) ? event.getEmail() : "customer@stickybeak.com.au";
        String subject = "[StickyBeak] Order Confirmed: " + event.getOrderNo();

        try {
            Context context = new Context();
            context.setVariable("orderNo", event.getOrderNo());
            context.setVariable("amount", event.getAmount());
            context.setVariable("currency", event.getCurrency() != null ? event.getCurrency() : "AUD");
            context.setVariable("paymentMethod", event.getPaymentMethod() != null ? event.getPaymentMethod() : "Card");
            context.setVariable("transactionId", event.getTransactionId());
            context.setVariable("payTime", event.getPayTime() != null ? event.getPayTime().toString().replace("T", " ") : "");
            context.setVariable("items", event.getItems());

            String htmlContent = templateEngine.process("order-confirmation", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom("orders@stickybeak.com.au", "StickyBeak");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent order confirmation email for order {} to {}", event.getOrderNo(), recipient);

        } catch (Exception e) {
            log.error("Failed to send order confirmation email for order {}: {}", event.getOrderNo(), e.getMessage(), e);
        }
    }
}

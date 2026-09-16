package au.com.stickybeak.notification.service;

import au.com.stickybeak.common.event.OrderPaidEvent;
import au.com.stickybeak.notification.service.impl.EmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private TemplateEngine templateEngine;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        templateEngine = mock(TemplateEngine.class);
        emailService = new EmailServiceImpl(mailSender, templateEngine);

        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    @Test
    void testSendOrderConfirmation() throws Exception {
        OrderPaidEvent event = new OrderPaidEvent();
        event.setOrderNo("SO202609160001");
        event.setEmail("buyer@example.com");
        event.setAmount(new BigDecimal("49.90"));
        event.setCurrency("AUD");
        event.setPaymentMethod("card");
        event.setTransactionId("pi_test_123");
        event.setPayTime(LocalDateTime.now());

        when(templateEngine.process(eq("order-confirmation"), any(Context.class)))
                .thenReturn("<html><body>Order Confirmed</body></html>");

        emailService.sendOrderConfirmation(event);

        verify(templateEngine, times(1)).process(eq("order-confirmation"), any(Context.class));
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sentMsg = captor.getValue();
        assertNotNull(sentMsg);
    }
}

package au.com.stickybeak.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 基础冒烟测试（不启动 Spring 上下文，避免依赖外部 Nacos/RabbitMQ）。
 */
class NotificationApplicationTests {

    @Test
    void applicationClassExists() {
        assertNotNull(NotificationApplication.class);
    }
}

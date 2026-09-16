package au.com.stickybeak.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 基础冒烟测试（不启动 Spring 上下文，避免依赖外部中间件）。
 */
class PaymentApplicationTests {

    @Test
    void applicationClassExists() {
        assertNotNull(PaymentApplication.class);
    }
}

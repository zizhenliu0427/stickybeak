package au.com.stickybeak.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 基础冒烟测试（不启动 Spring 上下文，避免依赖外部中间件）。
 */
class OrderApplicationTests {

    @Test
    void applicationClassExists() {
        assertNotNull(OrderApplication.class);
    }
}

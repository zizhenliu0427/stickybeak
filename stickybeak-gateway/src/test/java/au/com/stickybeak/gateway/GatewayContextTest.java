package au.com.stickybeak.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 启动完整的网关上下文（不连 Nacos；Redis 只在鉴权请求时才连接）。
 * Sprint 9 起网关因为重复定义 sentinelGatewayFilter 一直无法启动，而单测从不加载 Spring 上下文，CI 没有发现；
 * 这个测试保证"网关能启动"本身被 CI 覆盖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.sentinel.transport.dashboard="
})
class GatewayContextTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedApiStillRequiresLogin() {
        webTestClient.get().uri("/api/orders/my")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}

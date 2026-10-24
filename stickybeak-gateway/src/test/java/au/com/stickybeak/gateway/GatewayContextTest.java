package au.com.stickybeak.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
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

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void docsAggregationListsEveryServiceWithoutLogin() {
        webTestClient.get().uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls.length()").isEqualTo(5)
                .jsonPath("$.urls[?(@.url == '/api-docs/product/v3/api-docs')]").exists();

        webTestClient.get().uri("/doc.html")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void agentRouteForwardsToPythonService() {
        Route agent = routeLocator.getRoutes().filter(r -> "agent".equals(r.getId())).blockFirst();
        assertNotNull(agent);
        assertEquals("lb://stickybeak-agent", agent.getUri().toString());

        webTestClient.post().uri("/api/agent/chat")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedApiStillRequiresLogin() {
        webTestClient.get().uri("/api/orders/my")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}

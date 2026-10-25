package au.com.stickybeak.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SentinelGatewayConfigTest {

    private final SentinelGatewayConfig config = new SentinelGatewayConfig(new ObjectMapper());

    @Test
    void loadsRouteRateLimitRules() {
        config.initGatewayRules();

        Set<GatewayFlowRule> rules = GatewayRuleManager.getRules();
        assertTrue(rules.stream().anyMatch(r -> "order".equals(r.getResource()) && r.getCount() == 100));
        assertTrue(rules.stream().anyMatch(r -> "auth".equals(r.getResource()) && r.getCount() == 60));
        assertTrue(rules.stream().anyMatch(r -> "agent".equals(r.getResource()) && r.getCount() == 20));
        assertTrue(rules.stream().anyMatch(r -> "mcp".equals(r.getResource()) && r.getCount() == 30));
    }

    @Test
    void blockedRequestsGet429Json() {
        BlockRequestHandler handler = config.sentinelBlockRequestHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders/my"));

        ServerResponse response = handler.handleRequest(exchange, new FlowException("order")).block();

        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.headers().getContentType());
    }
}

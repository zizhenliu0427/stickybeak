package au.com.stickybeak.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SentinelGatewayConfigTest {

    @Test
    void testInitGatewayRulesAndFilters() {
        @SuppressWarnings("unchecked")
        ObjectProvider<List<ViewResolver>> viewResolversProvider = mock(ObjectProvider.class);
        ServerCodecConfigurer codecConfigurer = mock(ServerCodecConfigurer.class);
        ObjectMapper objectMapper = new ObjectMapper();

        SentinelGatewayConfig config = new SentinelGatewayConfig(viewResolversProvider, codecConfigurer, objectMapper);

        assertNotNull(config.sentinelGatewayFilter());
        assertNotNull(config.sentinelGatewayBlockExceptionHandler());

        config.init();

        Set<GatewayFlowRule> rules = GatewayRuleManager.getRules();
        assertNotNull(rules);
        assertTrue(rules.stream().anyMatch(r -> "order".equals(r.getResource())));
        assertTrue(rules.stream().anyMatch(r -> "auth".equals(r.getResource())));
    }
}

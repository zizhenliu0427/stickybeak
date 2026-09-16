package au.com.stickybeak.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SentinelGatewayConfigTest {

    @Test
    void testInitGatewayRules() {
        // sentinelGatewayFilter / blockHandler Bean 由 SentinelSCGAutoConfiguration 自动注册（不再手动声明），
        // 本测试只验证规则加载与自定义 429 处理器注册。
        SentinelGatewayConfig config = new SentinelGatewayConfig(new ObjectMapper());

        config.init();

        Set<GatewayFlowRule> rules = GatewayRuleManager.getRules();
        assertNotNull(rules);
        assertTrue(rules.stream().anyMatch(r -> "order".equals(r.getResource())));
        assertTrue(rules.stream().anyMatch(r -> "auth".equals(r.getResource())));
        assertNotNull(com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager.getBlockHandler());
    }
}

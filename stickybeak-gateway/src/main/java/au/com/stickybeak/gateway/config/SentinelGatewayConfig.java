package au.com.stickybeak.gateway.config;

import au.com.stickybeak.common.result.Result;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel Gateway Flow Control & Block Handler Configuration.
 * Intercepts high-frequency requests, protects downstream services, and responds with unified HTTP 429 JSON.
 * <p>
 * SentinelGatewayFilter 与 SentinelGatewayBlockExceptionHandler 由 spring-cloud-alibaba-sentinel-gateway 的
 * SentinelSCGAutoConfiguration 注册。这里曾经再声明一次同名 Bean，Spring Boot 默认禁止覆盖，网关直接启动失败；
 * 现在只提供限流规则和 BlockRequestHandler，后者由自动配置在启动时注册到 GatewayCallbackManager。
 */
@Configuration
public class SentinelGatewayConfig {

    private final ObjectMapper objectMapper;

    public SentinelGatewayConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public BlockRequestHandler sentinelBlockRequestHandler() {
        return (exchange, t) -> {
            Result<Void> failResult = Result.fail(429, "Too many requests. Sentinel gateway rate limit exceeded. Please try again later.");
            String jsonBody;
            try {
                jsonBody = objectMapper.writeValueAsString(failResult);
            } catch (JsonProcessingException e) {
                jsonBody = "{\"code\":429,\"message\":\"Too many requests.\",\"data\":null}";
            }

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(jsonBody));
        };
    }

    @PostConstruct
    public void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // Rate limit for 'order' route: max 100 QPS under normal conditions
        rules.add(new GatewayFlowRule("order")
                .setCount(100)
                .setIntervalSec(1));

        // Rate limit for 'auth' route: max 60 QPS (prevent brute-force login/registration attacks)
        rules.add(new GatewayFlowRule("auth")
                .setCount(60)
                .setIntervalSec(1));

        // Rate limit for 'agent' route: max 20 QPS in total; each model call is slow and costs money.
        // Per-user limits and daily quotas are enforced by the agent itself (Redis counters).
        rules.add(new GatewayFlowRule("agent")
                .setCount(20)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }
}

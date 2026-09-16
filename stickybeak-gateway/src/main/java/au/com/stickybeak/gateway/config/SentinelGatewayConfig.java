package au.com.stickybeak.gateway.config;

import au.com.stickybeak.common.result.Result;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel Gateway Flow Control & Block Handler Configuration.
 * Intercepts high-frequency requests, protects downstream services, and responds with unified HTTP 429 JSON.
 */
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;
    private final ObjectMapper objectMapper;

    public SentinelGatewayConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                                 ServerCodecConfigurer serverCodecConfigurer,
                                 ObjectMapper objectMapper) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
        this.objectMapper = objectMapper;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    @PostConstruct
    public void init() {
        initCustomBlockHandler();
        initGatewayRules();
    }

    private void initCustomBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
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
        });
    }

    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // Rate limit for 'order' route: max 100 QPS under normal conditions
        rules.add(new GatewayFlowRule("order")
                .setCount(100)
                .setIntervalSec(1));

        // Rate limit for 'auth' route: max 60 QPS (prevent brute-force login/registration attacks)
        rules.add(new GatewayFlowRule("auth")
                .setCount(60)
                .setIntervalSec(1));

        GatewayRuleManager.loadRules(rules);
    }
}

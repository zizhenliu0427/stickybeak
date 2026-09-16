package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 故事线 9.4 演示端点：Nacos 配置中心多环境 + 动态刷新。
 * app.demo.* 来自 Nacos dataId（stickybeak-product-{profile}.yml），
 * @RefreshScope 让配置变更无需重启即生效。
 */
@RestController
@RefreshScope
@RequestMapping("/products")
public class ConfigDemoController {

    @Value("${app.demo.store-banner:本地默认横幅（Nacos 未下发配置）}")
    private String storeBanner;

    @Value("${app.demo.list-cache-ttl-seconds:60}")
    private int listCacheTtl;

    private final Environment environment;

    public ConfigDemoController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/config-demo")
    public Result<Map<String, Object>> demo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("storeBanner", storeBanner);
        data.put("listCacheTtlSeconds", listCacheTtl);
        data.put("activeProfiles", environment.getActiveProfiles());
        data.put("namespace", environment.getProperty("spring.cloud.nacos.config.namespace", "public"));
        return Result.ok(data);
    }
}

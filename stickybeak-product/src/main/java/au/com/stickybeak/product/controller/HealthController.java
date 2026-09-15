package au.com.stickybeak.product.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import au.com.stickybeak.common.result.Result;

@RestController
@RequestMapping("/products/health")
public class HealthController {

    @GetMapping
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "service", "stickybeak-product",
                "status", "UP",
                "timestamp", System.currentTimeMillis()));
    }
}

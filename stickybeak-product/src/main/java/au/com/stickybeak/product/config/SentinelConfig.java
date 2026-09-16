package au.com.stickybeak.product.config;

import au.com.stickybeak.product.service.impl.ProductSearchServiceImpl;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Sentinel 熔断规则：ES 检索异常比例 > 50%（60s 统计窗口，至少 5 个请求）→ 熔断 30s，
 * 期间调用直接走 fallback 降级 MySQL，不再打挂掉的 ES。
 */
@Configuration
public class SentinelConfig {

    @PostConstruct
    public void initDegradeRules() {
        DegradeRule rule = new DegradeRule(ProductSearchServiceImpl.RESOURCE)
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5)
                .setTimeWindow(30)
                .setStatIntervalMs(60_000)
                .setMinRequestAmount(5);
        DegradeRuleManager.loadRules(Collections.singletonList(rule));
    }
}


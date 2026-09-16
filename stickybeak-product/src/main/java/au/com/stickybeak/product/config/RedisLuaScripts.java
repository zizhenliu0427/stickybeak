package au.com.stickybeak.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLuaScripts {

    public static final String DEDUCT_STOCK_LUA =
            "local stock = redis.call('get', KEYS[1]); " +
            "if not stock then " +
            "    return -1; " +
            "end; " +
            "stock = tonumber(stock); " +
            "local delta = tonumber(ARGV[1]); " +
            "if stock >= delta then " +
            "    redis.call('decrby', KEYS[1], delta); " +
            "    return 1; " +
            "else " +
            "    return 0; " +
            "end;";

    @Bean
    public DefaultRedisScript<Long> deductStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DEDUCT_STOCK_LUA);
        script.setResultType(Long.class);
        return script;
    }
}

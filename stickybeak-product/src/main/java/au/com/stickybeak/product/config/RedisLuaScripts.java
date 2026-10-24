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

    /**
     * 只在 key 存在时回补。key 过期或被删除后直接 INCRBY 会凭空创建一个值为 qty 的错误库存；
     * key 不存在时下一次扣减会从 MySQL 重新预热，无需回补。
     */
    public static final String INCREMENT_IF_EXISTS_LUA =
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "    return redis.call('incrby', KEYS[1], ARGV[1]); " +
            "end; " +
            "return -1;";

    @Bean
    public DefaultRedisScript<Long> deductStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(DEDUCT_STOCK_LUA);
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> incrementIfExistsScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(INCREMENT_IF_EXISTS_LUA);
        script.setResultType(Long.class);
        return script;
    }
}

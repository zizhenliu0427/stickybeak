package au.com.stickybeak.product.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLuaScripts {

    /**
     * KEYS[1] 商品库存 key，KEYS[2] 订单预扣账本（hash：商品 ID → 数量）；
     * ARGV[1] 数量，ARGV[2] 商品 ID，ARGV[3] 账本过期秒数。
     * 扣减与记账在同一个脚本里完成：只要 Redis 扣了，账本里就一定有记录，事务没提交时能按账本原样归还。
     */
    public static final String DEDUCT_STOCK_LUA =
            "local stock = redis.call('get', KEYS[1]); " +
            "if not stock then " +
            "    return -1; " +
            "end; " +
            "stock = tonumber(stock); " +
            "local delta = tonumber(ARGV[1]); " +
            "if stock >= delta then " +
            "    redis.call('decrby', KEYS[1], delta); " +
            "    redis.call('hincrby', KEYS[2], ARGV[2], delta); " +
            "    redis.call('expire', KEYS[2], ARGV[3]); " +
            "    return 1; " +
            "else " +
            "    return 0; " +
            "end;";

    /**
     * KEYS[1] 订单预扣账本；ARGV[1] 库存 key 前缀。按账本归还 Redis 库存后删除账本，返回归还的商品数。
     * 读取与删除是原子的，本地回滚与 Seata 全局回滚 hook 都调用也只会归还一次。
     * 库存 key 由账本内容拼出，只适用于单节点 Redis（Cluster 下要求所有 key 通过 KEYS 传入）。
     */
    public static final String RESTORE_RESERVATION_LUA =
            "local entries = redis.call('hgetall', KEYS[1]); " +
            "for i = 1, #entries, 2 do " +
            "    local stockKey = ARGV[1] .. entries[i]; " +
            "    if redis.call('exists', stockKey) == 1 then " +
            "        redis.call('incrby', stockKey, entries[i + 1]); " +
            "    end; " +
            "end; " +
            "redis.call('del', KEYS[1]); " +
            "return #entries / 2;";

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

    @Bean
    public DefaultRedisScript<Long> restoreReservationScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(RESTORE_RESERVATION_LUA);
        script.setResultType(Long.class);
        return script;
    }
}

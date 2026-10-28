package au.com.stickybeak.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

/**
 * 全局 Long → String 序列化（雪花 ID 超 JS Number.MAX_SAFE_INTEGER 会精度丢失，
 * 前端拿到错误 id 调接口 404）。Long 与 long 都按字符串输出；Integer 计数类字段不受影响。
 */
@Component
public class JacksonLongConfig implements Jackson2ObjectMapperBuilderCustomizer {

    @Override
    public void customize(Jackson2ObjectMapperBuilder builder) {
        builder.serializerByType(Long.class, ToStringSerializer.instance);
        builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}

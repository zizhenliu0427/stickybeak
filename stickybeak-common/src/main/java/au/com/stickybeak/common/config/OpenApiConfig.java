package au.com.stickybeak.common.config;

import au.com.stickybeak.common.internal.InternalApi;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI 3.0 & Knife4j Configuration for StickyBeak Microservices.
 * 各服务的文档经网关 /doc.html 聚合（见 gateway application.yml 的 knife4j.gateway）。
 */
@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    static final String INTERNAL_TAG = "internal";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI customOpenAPI(@Value("${spring.application.name:stickybeak}") String applicationName) {
        return new OpenAPI()
                .info(new Info()
                        .title("StickyBeak API · " + applicationName)
                        .version("v1.3.0")
                        .description("StickyBeak Aussie Fridge Magnets Microservices Platform — OpenAPI 3.0 & Knife4j Specification")
                        .contact(new Contact().name("StickyBeak Engineering").email("dev@stickybeak.com.au"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    /** 网关（WebFlux）也依赖 common，但没有 springdoc webmvc，单独按类是否存在加载 */
    @Configuration
    @ConditionalOnClass(name = "org.springdoc.core.customizers.OperationCustomizer")
    static class InternalApiDocs {

        /** @InternalApi 接口照常出现在文档里，但标明只供服务间调用，网关会拒绝外部请求 */
        @Bean
        public OperationCustomizer internalApiOperationCustomizer() {
            return (operation, handlerMethod) -> {
                boolean internal = handlerMethod.hasMethodAnnotation(InternalApi.class)
                        || handlerMethod.getBeanType().isAnnotationPresent(InternalApi.class);
                if (internal) {
                    operation.addTagsItem(INTERNAL_TAG);
                    String summary = operation.getSummary() == null ? "" : " " + operation.getSummary();
                    operation.setSummary("[内部接口]" + summary);
                    operation.setDescription("仅供服务间调用（需 X-Internal-Call 头），经网关的外部请求会被拒绝。"
                            + (operation.getDescription() == null ? "" : "\n\n" + operation.getDescription()));
                }
                return operation;
            };
        }
    }
}

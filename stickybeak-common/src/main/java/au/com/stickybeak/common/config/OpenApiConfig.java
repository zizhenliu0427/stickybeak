package au.com.stickybeak.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI 3.0 & Knife4j Configuration for StickyBeak Microservices
 */
@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfig {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("StickyBeak E-Commerce API Documentation")
                        .version("v1.0.0")
                        .description("StickyBeak Aussie Fridge Magnets Microservices Platform — OpenAPI 3.0 & Knife4j Specification")
                        .contact(new Contact().name("StickyBeak Engineering").email("dev@stickybeak.com.au"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}

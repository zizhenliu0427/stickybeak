package au.com.stickybeak.order.config;

import au.com.stickybeak.common.internal.InternalCallRequestInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // 订单服务的下游调用都是服务间调用，统一带上内部调用标识（库存扣减、支付会话等 @InternalApi 接口需要）
        restTemplate.getInterceptors().add(new InternalCallRequestInterceptor());
        return restTemplate;
    }
}

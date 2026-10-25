package au.com.stickybeak.common.internal;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 服务间 RestTemplate 调用自动带上内部调用标识。
 */
public class InternalCallRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(InternalCall.HEADER, InternalCall.VALUE);
        return execution.execute(request, body);
    }
}

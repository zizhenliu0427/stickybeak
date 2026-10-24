package au.com.stickybeak.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把 SkyWalking traceId 写进响应头 {@value #HEADER}。trace 从网关开始，下游服务拿到的是同一个 traceId，
 * 网关原样透传给浏览器；用户报错时带上它，就能在 SkyWalking 里直接搜到这次请求的完整链路和日志。
 * 没挂 agent 时 traceId 为空或 "N/A"，不写响应头。仅 Servlet 应用加载（网关是 WebFlux）。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TraceIdResponseFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceContext.traceId();
        if (StringUtils.hasText(traceId) && !"N/A".equals(traceId)) {
            response.setHeader(HEADER, traceId);
        }
        chain.doFilter(request, response);
    }
}

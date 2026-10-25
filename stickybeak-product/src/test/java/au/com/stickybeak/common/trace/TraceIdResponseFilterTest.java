package au.com.stickybeak.common.trace;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

/** common 模块没有测试依赖，过滤器的测试放在使用它的 servlet 服务里 */
class TraceIdResponseFilterTest {

    private final TraceIdResponseFilter filter = new TraceIdResponseFilter();

    private MockHttpServletResponse run() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/products"), response, chain);
        assertNotNull(chain.getRequest(), "request must continue down the chain");
        return response;
    }

    @Test
    void writesTraceIdWhenAgentIsAttached() throws Exception {
        try (MockedStatic<TraceContext> ctx = mockStatic(TraceContext.class)) {
            ctx.when(TraceContext::traceId).thenReturn("7f3c5a8e2b1d4c6f.84.17297635000010001");

            assertEquals("7f3c5a8e2b1d4c6f.84.17297635000010001", run().getHeader(TraceIdResponseFilter.HEADER));
        }
    }

    @Test
    void skipsHeaderWithoutAgent() throws Exception {
        // 没挂 agent：工具包返回空串
        assertNull(run().getHeader(TraceIdResponseFilter.HEADER));

        try (MockedStatic<TraceContext> ctx = mockStatic(TraceContext.class)) {
            ctx.when(TraceContext::traceId).thenReturn("N/A");
            assertNull(run().getHeader(TraceIdResponseFilter.HEADER));
        }
    }
}

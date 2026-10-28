package au.com.stickybeak.product.controller;

import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.internal.InternalApiInterceptor;
import au.com.stickybeak.common.internal.InternalCall;
import au.com.stickybeak.product.dto.StockDeductRequest;
import au.com.stickybeak.product.dto.StockRollbackRequest;
import au.com.stickybeak.product.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class InventoryControllerGuardTest {

    private final InternalApiInterceptor interceptor = new InternalApiInterceptor();
    private final InventoryController controller = new InventoryController(mock(InventoryService.class));

    @Test
    void stockWritesRequireInternalCall() throws Exception {
        HandlerMethod[] writes = {
                new HandlerMethod(controller, InventoryController.class.getMethod("deductStock", StockDeductRequest.class)),
                new HandlerMethod(controller, InventoryController.class.getMethod("rollbackStock", StockRollbackRequest.class)),
                new HandlerMethod(controller, InventoryController.class.getMethod("confirmStock", StockRollbackRequest.class)),
                new HandlerMethod(controller, InventoryController.class.getMethod("restockStock", StockRollbackRequest.class))
        };
        for (HandlerMethod handler : writes) {
            assertThrows(BusinessException.class,
                    () -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));

            MockHttpServletRequest internal = new MockHttpServletRequest();
            internal.addHeader(InternalCall.HEADER, InternalCall.VALUE);
            assertDoesNotThrow(() -> interceptor.preHandle(internal, new MockHttpServletResponse(), handler));
        }
    }

    @Test
    void stockQueryStaysPublic() throws Exception {
        HandlerMethod handler = new HandlerMethod(controller, InventoryController.class.getMethod("getStock", Long.class));
        assertDoesNotThrow(() -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler));
    }
}

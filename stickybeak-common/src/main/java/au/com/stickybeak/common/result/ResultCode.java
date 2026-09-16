package au.com.stickybeak.common.result;

/**
 * Unified business/HTTP result codes.
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    CONFLICT(409, "conflict"),
    UNPROCESSABLE(422, "unprocessable entity"),
    SYSTEM_ERROR(500, "internal server error"),
    BUSINESS_ERROR(1000, "business error"),
    STOCK_NOT_ENOUGH(1001, "insufficient stock"),
    INSUFFICIENT_STOCK(1001, "insufficient stock"),
    ORDER_STATE_ILLEGAL(1002, "illegal order state transition"),
    ILLEGAL_STATE_TRANSITION(1002, "illegal order state transition"),
    PAYMENT_FAILED(1003, "payment failed"),
    ORDER_ALREADY_CLOSED(1004, "order already closed");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

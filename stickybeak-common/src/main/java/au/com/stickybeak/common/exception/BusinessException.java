package au.com.stickybeak.common.exception;

import au.com.stickybeak.common.result.ResultCode;

/**
 * Business exception carrying a unified result code.
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    public BusinessException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BusinessException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }

    public int getCode() {
        return code;
    }
}

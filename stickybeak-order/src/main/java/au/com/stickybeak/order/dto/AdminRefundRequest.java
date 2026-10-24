package au.com.stickybeak.order.dto;

import jakarta.validation.constraints.Size;
import java.io.Serializable;

public class AdminRefundRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @Size(max = 200, message = "Refund reason must be at most 200 characters")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

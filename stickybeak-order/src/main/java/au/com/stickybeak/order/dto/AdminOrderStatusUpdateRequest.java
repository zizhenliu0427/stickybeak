package au.com.stickybeak.order.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

public class AdminOrderStatusUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Target status cannot be blank")
    private String status;

    private String trackingNo;

    private String remark;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

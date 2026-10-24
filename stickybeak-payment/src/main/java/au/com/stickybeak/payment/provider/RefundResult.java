package au.com.stickybeak.payment.provider;

public class RefundResult {

    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_PENDING = "pending";

    private final String refundId;
    /** 渠道返回的退款状态：succeeded / pending / requires_action / failed / canceled */
    private final String status;

    public RefundResult(String refundId, String status) {
        this.refundId = refundId;
        this.status = status;
    }

    public String getRefundId() {
        return refundId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isSucceeded() {
        return STATUS_SUCCEEDED.equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status) || "canceled".equals(status);
    }
}

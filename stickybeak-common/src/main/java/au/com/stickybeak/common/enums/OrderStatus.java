package au.com.stickybeak.common.enums;

/**
 * Order lifecycle status enum.
 */
public enum OrderStatus {

    PENDING("pending", "待支付"),
    PAID("paid", "已支付"),
    PROCESSING("processing", "备货中"),
    SHIPPED("shipped", "已发货"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public static OrderStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}

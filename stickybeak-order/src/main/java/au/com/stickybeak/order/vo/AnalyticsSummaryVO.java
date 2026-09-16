package au.com.stickybeak.order.vo;

import java.io.Serializable;
import java.math.BigDecimal;

public class AnalyticsSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal totalGmv;
    private BigDecimal todayGmv;
    private Long totalOrders;
    private Long paidOrders;
    private Long todayOrders;
    private Long pendingShipmentOrders; // paid or processing
    private Long pendingPaymentOrders;  // pending

    public BigDecimal getTotalGmv() {
        return totalGmv;
    }

    public void setTotalGmv(BigDecimal totalGmv) {
        this.totalGmv = totalGmv;
    }

    public BigDecimal getTodayGmv() {
        return todayGmv;
    }

    public void setTodayGmv(BigDecimal todayGmv) {
        this.todayGmv = todayGmv;
    }

    public Long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Long getPaidOrders() {
        return paidOrders;
    }

    public void setPaidOrders(Long paidOrders) {
        this.paidOrders = paidOrders;
    }

    public Long getTodayOrders() {
        return todayOrders;
    }

    public void setTodayOrders(Long todayOrders) {
        this.todayOrders = todayOrders;
    }

    public Long getPendingShipmentOrders() {
        return pendingShipmentOrders;
    }

    public void setPendingShipmentOrders(Long pendingShipmentOrders) {
        this.pendingShipmentOrders = pendingShipmentOrders;
    }

    public Long getPendingPaymentOrders() {
        return pendingPaymentOrders;
    }

    public void setPendingPaymentOrders(Long pendingPaymentOrders) {
        this.pendingPaymentOrders = pendingPaymentOrders;
    }
}

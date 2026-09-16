package au.com.stickybeak.product.vo;

import java.io.Serializable;
import java.util.List;

public class StockAlertSummaryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int totalAlerts;
    private int outOfStockCount;
    private int criticalCount;
    private int lowStockCount;
    private List<AdminProductVO> alertProducts;

    public int getTotalAlerts() {
        return totalAlerts;
    }

    public void setTotalAlerts(int totalAlerts) {
        this.totalAlerts = totalAlerts;
    }

    public int getOutOfStockCount() {
        return outOfStockCount;
    }

    public void setOutOfStockCount(int outOfStockCount) {
        this.outOfStockCount = outOfStockCount;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public void setCriticalCount(int criticalCount) {
        this.criticalCount = criticalCount;
    }

    public int getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(int lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public List<AdminProductVO> getAlertProducts() {
        return alertProducts;
    }

    public void setAlertProducts(List<AdminProductVO> alertProducts) {
        this.alertProducts = alertProducts;
    }
}

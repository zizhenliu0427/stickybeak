package au.com.stickybeak.order.vo;

import java.io.Serializable;
import java.math.BigDecimal;

public class SalesTrendItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String date; // YYYY-MM-DD
    private Integer orderCount;
    private BigDecimal gmv;

    public SalesTrendItemVO() {
    }

    public SalesTrendItemVO(String date, Integer orderCount, BigDecimal gmv) {
        this.date = date;
        this.orderCount = orderCount;
        this.gmv = gmv;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getGmv() {
        return gmv;
    }

    public void setGmv(BigDecimal gmv) {
        this.gmv = gmv;
    }
}

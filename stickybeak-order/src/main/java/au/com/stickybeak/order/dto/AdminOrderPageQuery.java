package au.com.stickybeak.order.dto;

import java.io.Serializable;

public class AdminOrderPageQuery implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer page = 1;
    private Integer size = 10;
    private String orderNo;
    private String status;
    private Long userId;
    private String startDate; // yyyy-MM-dd
    private String endDate;   // yyyy-MM-dd

    public Integer getPage() {
        return page != null && page > 0 ? page : 1;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size != null && size > 0 && size <= 100 ? size : 10;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}

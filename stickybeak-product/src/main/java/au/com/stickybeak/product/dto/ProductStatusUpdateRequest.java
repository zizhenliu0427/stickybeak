package au.com.stickybeak.product.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public class ProductStatusUpdateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 0: 上架 (STATUS_ACTIVE), 1: 下架 (STATUS_UNLISTED)
     */
    @NotNull(message = "Status cannot be null")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

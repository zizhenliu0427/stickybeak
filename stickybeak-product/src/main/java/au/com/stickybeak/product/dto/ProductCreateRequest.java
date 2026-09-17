package au.com.stickybeak.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端新建商品入参。金额为 AUD 元（与现有 admin 改价接口一致），图片为已上传完成的公网 URL 列表。
 */
public record ProductCreateRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 128)
        String name,

        /** 可空：缺省自动生成 p-{id} */
        @Size(max = 160)
        String slug,

        @NotNull(message = "must not be null")
        Long categoryId,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.01", message = "must be > 0")
        BigDecimal price,

        @NotNull(message = "must not be null")
        @Min(value = 0, message = "must be >= 0")
        Integer stock,

        @Size(max = 2000)
        String description,

        /** 图片公网 URL 列表，第一张为封面 */
        List<String> images,

        /** 标签名列表（不存在则自动创建） */
        List<String> tags,

        Boolean featured
) {
}

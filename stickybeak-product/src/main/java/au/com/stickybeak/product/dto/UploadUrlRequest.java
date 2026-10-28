package au.com.stickybeak.product.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 预签名上传申请。
 */
public record UploadUrlRequest(

        @NotBlank(message = "must not be blank")
        String filename,

        @NotBlank(message = "must not be blank")
        String contentType
) {
}

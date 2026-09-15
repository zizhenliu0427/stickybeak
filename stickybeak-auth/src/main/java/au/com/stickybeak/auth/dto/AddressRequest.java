package au.com.stickybeak.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 地址新建/更新入参。
 */
public record AddressRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 64)
        String receiver,

        @NotBlank(message = "must not be blank")
        @Size(max = 20)
        String phone,

        @Size(max = 64)
        String country,

        @NotBlank(message = "must not be blank")
        @Size(max = 64)
        String state,

        @NotBlank(message = "must not be blank")
        @Size(max = 64)
        String city,

        @NotBlank(message = "must not be blank")
        @Size(max = 16)
        String postcode,

        @NotBlank(message = "must not be blank")
        @Size(max = 255)
        String detail,

        Boolean isDefault
) {
}

package au.com.stickybeak.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /users/me 入参，字段均可选（只更新非 null 字段）。
 */
public record UpdateProfileRequest(

        @Size(max = 32, message = "length must be <= 32")
        String nickname,

        @Size(max = 20, message = "length must be <= 20")
        String phone,

        @Size(max = 255, message = "length must be <= 255")
        String avatarUrl
) {
}

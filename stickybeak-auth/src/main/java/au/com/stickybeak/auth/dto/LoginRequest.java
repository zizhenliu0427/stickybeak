package au.com.stickybeak.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。
 */
public record LoginRequest(

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email")
        String email,

        @NotBlank(message = "must not be blank")
        String password
) {
}

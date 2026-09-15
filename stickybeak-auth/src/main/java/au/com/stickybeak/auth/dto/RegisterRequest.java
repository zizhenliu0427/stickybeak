package au.com.stickybeak.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册入参。
 */
public record RegisterRequest(

        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email")
        @Size(max = 64, message = "length must be <= 64")
        String email,

        @NotBlank(message = "must not be blank")
        @Size(min = 8, max = 64, message = "length must be 8-64")
        String password,

        @Size(max = 32, message = "length must be <= 32")
        String nickname
) {
}

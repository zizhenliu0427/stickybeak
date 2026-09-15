package au.com.stickybeak.auth.vo;

/**
 * 地址视图。
 */
public record AddressVO(
        Long id,
        String receiver,
        String phone,
        String country,
        String state,
        String city,
        String postcode,
        String detail,
        Boolean isDefault
) {
}

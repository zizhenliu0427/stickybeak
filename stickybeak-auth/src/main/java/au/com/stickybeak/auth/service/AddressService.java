package au.com.stickybeak.auth.service;

import java.util.List;

import au.com.stickybeak.auth.dto.AddressRequest;
import au.com.stickybeak.auth.vo.AddressVO;

/**
 * 收货地址 CRUD（一人多地址）。
 */
public interface AddressService {

    List<AddressVO> list(Long userId);

    AddressVO create(Long userId, AddressRequest req);

    AddressVO update(Long userId, Long addressId, AddressRequest req);

    void delete(Long userId, Long addressId);
}

package au.com.stickybeak.auth.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import au.com.stickybeak.auth.dto.AddressRequest;
import au.com.stickybeak.auth.entity.Address;
import au.com.stickybeak.auth.mapper.AddressMapper;
import au.com.stickybeak.auth.service.AddressService;
import au.com.stickybeak.auth.vo.AddressVO;
import au.com.stickybeak.common.exception.BusinessException;
import au.com.stickybeak.common.result.ResultCode;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    public AddressServiceImpl(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    @Override
    public List<AddressVO> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .orderByDesc(Address::getIsDefault)
                        .orderByDesc(Address::getUpdateTime))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public AddressVO create(Long userId, AddressRequest req) {
        Address address = new Address();
        address.setUserId(userId);
        applyRequest(address, req);
        // 首个地址自动设为默认
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<Address>()
                .eq(Address::getUserId, userId));
        boolean makeDefault = Boolean.TRUE.equals(req.isDefault()) || count == null || count == 0;
        if (makeDefault) {
            clearDefault(userId);
            address.setIsDefault(1);
        }
        addressMapper.insert(address);
        return toVO(address);
    }

    @Override
    @Transactional
    public AddressVO update(Long userId, Long addressId, AddressRequest req) {
        Address address = getOwned(userId, addressId);
        applyRequest(address, req);
        if (Boolean.TRUE.equals(req.isDefault())) {
            clearDefault(userId);
            address.setIsDefault(1);
        } else if (Boolean.FALSE.equals(req.isDefault())) {
            address.setIsDefault(0);
        }
        addressMapper.updateById(address);
        return toVO(address);
    }

    @Override
    public void delete(Long userId, Long addressId) {
        Address address = getOwned(userId, addressId);
        addressMapper.deleteById(address.getId());
    }

    /** 越权访问他人地址按 404 处理，不暴露资源存在性 */
    private Address getOwned(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "address not found");
        }
        return address;
    }

    private void clearDefault(Long userId) {
        // 字符串列名：LambdaUpdateWrapper.set 即时解析列，纯单测下无 MP lambda 缓存会失败
        addressMapper.update(null, new UpdateWrapper<Address>()
                .eq("user_id", userId)
                .eq("is_default", 1)
                .set("is_default", 0));
    }

    private void applyRequest(Address address, AddressRequest req) {
        address.setReceiver(req.receiver().trim());
        address.setPhone(req.phone().trim());
        address.setCountry(req.country() == null || req.country().isBlank()
                ? "Australia" : req.country().trim());
        address.setState(req.state().trim());
        address.setCity(req.city().trim());
        address.setPostcode(req.postcode().trim());
        address.setDetail(req.detail().trim());
    }

    private AddressVO toVO(Address a) {
        return new AddressVO(a.getId(), a.getReceiver(), a.getPhone(), a.getCountry(),
                a.getState(), a.getCity(), a.getPostcode(), a.getDetail(),
                a.getIsDefault() != null && a.getIsDefault() == 1);
    }
}

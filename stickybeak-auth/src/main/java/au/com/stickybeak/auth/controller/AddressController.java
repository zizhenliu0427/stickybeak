package au.com.stickybeak.auth.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import au.com.stickybeak.auth.dto.AddressRequest;
import au.com.stickybeak.auth.security.SecurityUtils;
import au.com.stickybeak.auth.service.AddressService;
import au.com.stickybeak.auth.vo.AddressVO;
import au.com.stickybeak.common.result.Result;

/**
 * 收货地址 CRUD（一人多地址）。
 */
@RestController
@RequestMapping("/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public Result<List<AddressVO>> list() {
        return Result.ok(addressService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public Result<AddressVO> create(@Valid @RequestBody AddressRequest req) {
        return Result.ok(addressService.create(SecurityUtils.currentUserId(), req));
    }

    @PatchMapping("/{id}")
    public Result<AddressVO> update(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return Result.ok(addressService.update(SecurityUtils.currentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(SecurityUtils.currentUserId(), id);
        return Result.ok();
    }
}

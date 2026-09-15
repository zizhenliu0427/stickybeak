package au.com.stickybeak.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import au.com.stickybeak.auth.entity.Role;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}

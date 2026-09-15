package au.com.stickybeak.auth.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import au.com.stickybeak.auth.entity.Role;
import au.com.stickybeak.auth.entity.User;
import au.com.stickybeak.auth.entity.UserRole;
import au.com.stickybeak.auth.mapper.RoleMapper;
import au.com.stickybeak.auth.mapper.UserMapper;
import au.com.stickybeak.auth.mapper.UserRoleMapper;

/**
 * 幂等种子：三角色 + 开发用 admin 账号（admin@stickybeak.au / Admin123!，仅 seed-dev-admin=true 时）。
 * 生产环境务必 SEED_DEV_ADMIN=false。
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String DEV_ADMIN_EMAIL = "admin@stickybeak.au";
    private static final String DEV_ADMIN_PASSWORD = "Admin123!";

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedDevAdmin;

    public DataSeeder(RoleMapper roleMapper, UserMapper userMapper, UserRoleMapper userRoleMapper,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed-dev-admin:true}") boolean seedDevAdmin) {
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.seedDevAdmin = seedDevAdmin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRole(Role.CODE_CUSTOMER, "Customer");
        seedRole(Role.CODE_ADMIN, "Admin");
        seedRole(Role.CODE_SYSADMIN, "System Admin");
        if (seedDevAdmin) {
            seedDevAdmin();
        }
    }

    private void seedRole(String code, String name) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getCode, code));
        if (count == null || count == 0) {
            Role role = new Role();
            role.setCode(code);
            role.setName(name);
            roleMapper.insert(role);
            log.info("seeded role: {}", code);
        }
    }

    private void seedDevAdmin() {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, DEV_ADMIN_EMAIL));
        if (count != null && count > 0) {
            return;
        }
        User admin = new User();
        admin.setEmail(DEV_ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode(DEV_ADMIN_PASSWORD));
        admin.setNickname("StickyBeak Admin");
        admin.setStatus(User.STATUS_NORMAL);
        userMapper.insert(admin);

        Role adminRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getCode, Role.CODE_ADMIN));
        UserRole userRole = new UserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(adminRole.getId());
        userRoleMapper.insert(userRole);
        log.warn("seeded DEV admin account {} / {} — disable via SEED_DEV_ADMIN=false in production",
                DEV_ADMIN_EMAIL, DEV_ADMIN_PASSWORD);
    }
}

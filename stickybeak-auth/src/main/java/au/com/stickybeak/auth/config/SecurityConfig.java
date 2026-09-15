package au.com.stickybeak.auth.config;

import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import au.com.stickybeak.auth.security.HeaderAuthFilter;
import au.com.stickybeak.common.result.Result;
import au.com.stickybeak.common.result.ResultCode;

/**
 * 服务内安全配置。真正的 JWT 校验在网关；这里做兜底：
 * 公开端点放行，其余要求 HeaderAuthFilter 建立的认证上下文。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderAuthFilter headerAuthFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/logout",
                                "/auth/health", "/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, objectMapper, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, objectMapper, ResultCode.FORBIDDEN)));
        return http.build();
    }

    private static void writeError(HttpServletResponse res, ObjectMapper om, ResultCode rc)
            throws java.io.IOException {
        res.setStatus(rc.getCode());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(om.writeValueAsString(Result.fail(rc)));
    }
}

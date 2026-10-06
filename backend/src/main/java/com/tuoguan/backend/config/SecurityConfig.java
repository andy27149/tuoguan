package com.tuoguan.backend.config;

import com.tuoguan.backend.auth.dao.TeacherDao;
import com.tuoguan.backend.auth.security.JwtAuthenticationFilter;
import com.tuoguan.backend.auth.security.JwtService;
import com.tuoguan.backend.auth.security.MustChangePasswordFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final TeacherDao teacherDao;

    public SecurityConfig(JwtService jwtService, TeacherDao teacherDao) {
        this.jwtService = jwtService;
        this.teacherDao = teacherDao;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot 对 ERROR dispatcher 也会重新走一遍安全过滤链：未捕获异常转发到
                        // /error 时，如果这里不放行，会被 authenticationEntryPoint 拦成一个掩盖真实
                        // 500 的空白 401，调用方完全看不出后端真正发生了什么。
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/health", "/api/auth/login", "/api/public/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpStatus.UNAUTHORIZED.value())))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MustChangePasswordFilter(teacherDao), JwtAuthenticationFilter.class);
        return http.build();
    }
}

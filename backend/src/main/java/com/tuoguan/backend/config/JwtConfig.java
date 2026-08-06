package com.tuoguan.backend.config;

import com.tuoguan.backend.auth.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtService jwtService(@Value("${security.jwt.secret}") String secret) {
        return new JwtService(secret);
    }
}

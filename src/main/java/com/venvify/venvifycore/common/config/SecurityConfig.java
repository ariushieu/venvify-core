package com.venvify.venvifycore.common.config;

import com.venvify.venvifycore.common.security.JwtAccessDeniedHandler;
import com.venvify.venvifycore.common.security.JwtAuthenticationEntryPoint;
import com.venvify.venvifycore.common.security.JwtAuthenticationFilter;
import com.venvify.venvifycore.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security stateless + JWT (CLAUDE.md §4). Mở public cho /auth/** và swagger;
 * còn lại yêu cầu access token hợp lệ. Lỗi 401/403 trả JSON qua entry point / denied handler.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/auth/**",
            "/health",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/"
    };

    /** Origin được phép gọi API (CSV trong app.cors.allowed-origins). Mặc định dev server Vite. */
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String[] allowedOrigins;

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // Khách vãng lai duyệt event công khai. /events/mine phải đặt TRƯỚC vì
                        // pattern /events/* cũng khớp "mine" — matcher xét theo thứ tự, khớp đầu thắng.
                        .requestMatchers(HttpMethod.GET, "/events/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/events", "/events/*").permitAll()
                        // Storefront host public (P3 §2.4): profile + tab events. Follow (PUT/DELETE)
                        // và các path khác dưới /hosts vẫn rơi xuống authenticated.
                        .requestMatchers(HttpMethod.GET, "/hosts/*", "/hosts/*/events").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * CORS cho SPA (origin khác cổng). Token đi ở header Authorization nên không cần cookie
     * → allowCredentials=false, đủ cho phép GET/POST/PUT/PATCH/DELETE + preflight OPTIONS.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Auth là stateless JWT (xác thực thủ công ở AuthService; filter dựng Authentication từ claims),
     * KHÔNG dùng user/password store. Khai báo rỗng để Spring Boot không tự sinh "default security
     * password" — bean này không bao giờ được gọi (chain không có form/basic login).
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}

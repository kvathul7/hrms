package com.axvon.hrms.config;
import com.axvon.hrms.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.*;
@Configuration @EnableWebSecurity @EnableMethodSecurity @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(c->c.configurationSource(corsSource())).csrf(c->c.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/auth/**","/public/**").permitAll().anyRequest().authenticated())
            // Missing/expired/invalid token must be 401, not the default 403 — the frontend
            // interceptor keys off 401 to clear the session and bounce back to login.
            // A genuine role denial still falls through to 403.
            .exceptionHandling(e->e.authenticationEntryPoint(
                (req,res,ex)->res.sendError(HttpServletResponse.SC_UNAUTHORIZED,"Unauthorized")))
            .addFilterBefore(jwtAuthFilter,UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    @Bean public CorsConfigurationSource corsSource(){
        CorsConfiguration cfg=new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src=new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**",cfg);
        return src;
    }
    @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean public DaoAuthenticationProvider authProvider(){
        var p=new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }
    @Bean public AuthenticationManager authManager(AuthenticationConfiguration cfg) throws Exception{return cfg.getAuthenticationManager();}
}

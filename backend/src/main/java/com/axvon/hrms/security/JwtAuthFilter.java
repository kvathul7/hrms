package com.axvon.hrms.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component @RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    @Override
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        try {
            String bearer=req.getHeader("Authorization");
            if(StringUtils.hasText(bearer)&&bearer.startsWith("Bearer ")){
                String token=bearer.substring(7);
                String email=jwtUtil.extractEmail(token);
                if(email!=null&&SecurityContextHolder.getContext().getAuthentication()==null){
                    UserDetails ud=userDetailsService.loadUserByUsername(email);
                    if(jwtUtil.validate(token,ud)){
                        var auth=new UsernamePasswordAuthenticationToken(ud,null,ud.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        } catch(Exception ignored){}
        chain.doFilter(req,res);
    }
}

package com.axvon.hrms.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.*;
import java.util.function.Function;
@Component
public class JwtUtil {
    @Value("${axvon.jwt.secret}") private String secret;
    @Value("${axvon.jwt.expiration}") private long expiration;
    private Key key(){return Keys.hmacShaKeyFor(secret.getBytes());}
    public String generate(String email,String role){
        Map<String,Object> c=new HashMap<>();c.put("role",role);
        return Jwts.builder().setClaims(c).setSubject(email).setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis()+expiration))
            .signWith(key(),SignatureAlgorithm.HS256).compact();
    }
    public boolean validate(String token,UserDetails ud){
        try{return extractEmail(token).equals(ud.getUsername())&&!isExpired(token);}catch(Exception e){return false;}
    }
    public String extractEmail(String t){return claim(t,Claims::getSubject);}
    private boolean isExpired(String t){return claim(t,Claims::getExpiration).before(new Date());}
    private <T> T claim(String t,Function<Claims,T> f){return f.apply(Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(t).getBody());}
}

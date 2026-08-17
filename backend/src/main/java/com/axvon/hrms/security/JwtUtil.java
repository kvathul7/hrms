package com.axvon.hrms.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Key;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
@Component @Slf4j
public class JwtUtil {
    // Optional override. Left EMPTY in application.properties on purpose — see below.
    @Value("${axvon.jwt.secret:}") private String configured;
    @Value("${axvon.jwt.expiration}") private long expiration;
    private Key key;

    // The signing key used to be a literal in application.properties, so every copy of
    // the installer shipped the same one. Anyone holding the app (or reading the public
    // repository) could extract it and mint a valid token for any account whose email
    // address they could guess — no password needed. Each installation now generates its
    // own key on first start and keeps it next to its database.
    @PostConstruct
    void init(){ key = Keys.hmacShaKeyFor(resolveSecret().getBytes(StandardCharsets.UTF_8)); }

    private String resolveSecret(){
        String env = System.getenv("AXVON_JWT_SECRET");
        if(usable(env)){ log.info("JWT key: taken from AXVON_JWT_SECRET"); return env; }
        if(usable(configured)){ log.info("JWT key: taken from axvon.jwt.secret"); return configured; }
        Path f = Paths.get(System.getProperty("user.home"), ".axvon", "jwt.key");
        try{
            if(Files.exists(f)){
                String saved = Files.readString(f, StandardCharsets.UTF_8).trim();
                if(usable(saved)) return saved;
                log.warn("JWT key: {} is unusable, replacing it", f);
            }
            String generated = random();
            Files.createDirectories(f.getParent());
            Files.writeString(f, generated, StandardCharsets.UTF_8);
            log.warn("JWT key: generated a new signing key at {} — anyone signed in will need to sign in again", f);
            return generated;
        }catch(Exception e){
            // Never fall back to a shared constant: that is the whole problem being fixed
            // here. A key that lives only for this run means tokens stop working after a
            // restart, which is inconvenient but safe.
            log.error("JWT key: cannot read or write {} ({}) — using a key for this run only", f, e.getMessage());
            return random();
        }
    }
    private static String random(){
        byte[] raw = new byte[64];   // 512 bits; HS256 requires at least 256
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }
    private static boolean usable(String s){ return s != null && s.trim().length() >= 32; }

    public String generate(String email,String role){
        Map<String,Object> c=new HashMap<>();c.put("role",role);
        return Jwts.builder().setClaims(c).setSubject(email).setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis()+expiration))
            .signWith(key,SignatureAlgorithm.HS256).compact();
    }
    public boolean validate(String token,UserDetails ud){
        try{return extractEmail(token).equals(ud.getUsername())&&!isExpired(token);}catch(Exception e){return false;}
    }
    public String extractEmail(String t){return claim(t,Claims::getSubject);}
    private boolean isExpired(String t){return claim(t,Claims::getExpiration).before(new Date());}
    private <T> T claim(String t,Function<Claims,T> f){return f.apply(Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(t).getBody());}
}

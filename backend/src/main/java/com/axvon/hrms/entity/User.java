package com.axvon.hrms.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.*;
@Entity @Table(name="users") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private String email;
    // Employee embeds User, and /api/employees is readable by every role — without
    // this the BCrypt hash of every account ships to the browser on each page load.
    @JsonIgnore @Column(nullable=false) private String password;
    @Enumerated(EnumType.STRING) private Role role;
    @Builder.Default private Boolean isActive=true;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate(){createdAt=LocalDateTime.now();}
    // UserDetails plumbing — Spring Security needs these, JSON responses do not.
    @JsonIgnore @Override public Collection<? extends GrantedAuthority> getAuthorities(){return List.of(new SimpleGrantedAuthority("ROLE_"+role.name()));}
    @JsonIgnore @Override public String getUsername(){return email;}
    @JsonIgnore @Override public boolean isAccountNonExpired(){return true;}
    @JsonIgnore @Override public boolean isAccountNonLocked(){return true;}
    @JsonIgnore @Override public boolean isCredentialsNonExpired(){return true;}
    @JsonIgnore @Override public boolean isEnabled(){return isActive!=null&&isActive;}
    public enum Role{SUPER_ADMIN,HR_ADMIN,MANAGER,EMPLOYEE}
}

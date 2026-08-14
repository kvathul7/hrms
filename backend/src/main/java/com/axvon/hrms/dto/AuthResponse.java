package com.axvon.hrms.dto;
import lombok.*;
@Data @AllArgsConstructor @Builder
public class AuthResponse {
    private String token,email,role,name,message;
    private Long userId;
}

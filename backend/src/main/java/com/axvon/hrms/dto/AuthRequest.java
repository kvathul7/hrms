package com.axvon.hrms.dto;
import lombok.*;
// The login endpoint accepts a plain Map for backward compatibility with the shipped
// frontend; this is the typed shape of the same body, used by /auth/refresh and by
// anything that wants a contract rather than a map.
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthRequest {
    private String email;
    private String password;
}

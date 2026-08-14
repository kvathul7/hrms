package com.axvon.hrms.dto;
import lombok.*;
@Data @AllArgsConstructor @NoArgsConstructor @Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    public static <T> ApiResponse<T> ok(T d){return ApiResponse.<T>builder().success(true).message("Success").data(d).build();}
    public static <T> ApiResponse<T> ok(String m,T d){return ApiResponse.<T>builder().success(true).message(m).data(d).build();}
    public static <T> ApiResponse<T> error(String m){return ApiResponse.<T>builder().success(false).message(m).build();}
}

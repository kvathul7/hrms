package com.axvon.hrms.exception;

import com.axvon.hrms.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.NoSuchElementException;

@RestControllerAdvice @Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> badCreds(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Invalid credentials"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("You don't have permission for this action"));
    }

    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Record not found"));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request: " + (e.getMessage() == null ? "bad input" : e.getMessage().split("\n")[0])));
    }

    // A duplicate email / employee ID / holiday date is a user mistake, not a server
    // fault. Without this it surfaced as a 500 "Something went wrong", which told the
    // person filling the form nothing about what to change.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        String u = cause == null ? "" : cause.toUpperCase();
        String why;
        if (u.contains("NULL NOT ALLOWED")) why = "a required field was left empty";
        else if (u.contains("EMAIL")) why = "that email address is already registered";
        else if (u.contains("EMP_ID")) why = "that employee ID is already in use";
        else if (u.contains("HOLIDAY_DATE")) why = "a holiday already exists on that date";
        else if (u.contains("NAME")) why = "a record with that name already exists";
        else why = "it conflicts with an existing record";
        log.warn("Constraint violation: {}", cause);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("Could not save — " + why + "."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> generic(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Something went wrong. Check logs at ~/.axvon/logs"));
    }
}

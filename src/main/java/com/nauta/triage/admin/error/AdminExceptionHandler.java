package com.nauta.triage.admin.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.nauta.triage.admin")
public class AdminExceptionHandler {

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<ApiError> admin(AdminException e) {
        return ResponseEntity.status(e.status())
                .body(new ApiError(e.code(), e.getMessage(), e.details()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("missing_header", e.getHeaderName() + " header is required", Map.of()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> bad(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("validation_failed", e.getMessage(), Map.of()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> notFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("not_found", e.getMessage(), Map.of()));
    }
}

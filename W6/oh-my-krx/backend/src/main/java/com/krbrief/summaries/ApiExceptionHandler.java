package com.krbrief.summaries;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    if (LocalDate.class.equals(ex.getRequiredType())) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "error", "invalid_date",
                  "message", "date must be YYYY-MM-DD and a real calendar date",
                  "param", ex.getName(),
                  "value", ex.getValue() == null ? "" : String.valueOf(ex.getValue())));
    }

    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error", "invalid_parameter",
                "message", "request parameter type mismatch",
                "param", ex.getName()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleInvalid(MethodArgumentNotValidException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", "invalid_request", "message", "invalid request"));
  }
}

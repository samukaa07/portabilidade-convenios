package br.com.desafio.portabilidade.web;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(Exception e) {
        return ResponseEntity.status(409).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> notFound(Exception e) {
        return ResponseEntity.status(404).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    ResponseEntity<?> headerAusente(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Header obrigatório ausente (Idempotency-Key)"));
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<?> lock(Exception e) {
        return ResponseEntity.status(409).body(Map.of("erro", "Parcelas em processamento por outra solicitação; tente novamente"));
    }
}

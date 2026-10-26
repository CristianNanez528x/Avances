package com.restaurante.backend.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.restaurante.backend.dto.LoginRequest;
import com.restaurante.backend.dto.LoginResponse;
import com.restaurante.backend.service.AuthService;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;

    public AuthController(AuthService authService, RateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        enforceRateLimit();
        try {
            return authService.login(request.pin());
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("PIN inválido");
        }
    }

    private void enforceRateLimit() {
        if (!rateLimiter.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
    }
}
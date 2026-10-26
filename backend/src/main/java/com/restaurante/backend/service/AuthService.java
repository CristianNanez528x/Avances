package com.restaurante.backend.service;

import com.restaurante.backend.dto.LoginResponse;
import com.restaurante.backend.model.Usuario;
import com.restaurante.backend.repository.UsuarioRepository;
import com.restaurante.backend.utility.Roles;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String rawPin) {
        String pin = StringUtils.trimToEmpty(rawPin);
        if (!StringUtils.isNumeric(pin) || pin.length() != 4) {
            throw new IllegalArgumentException("PIN inválido");
        }

        Optional<Usuario> usuario = usuarioRepository.findByActivoTrue().stream()
            .filter(candidate -> passwordEncoder.matches(pin, candidate.getPin()))
            .findFirst();
        Usuario authenticated = usuario.orElseThrow(() -> new IllegalArgumentException("PIN inválido"));
        return new LoginResponse(
            jwtService.generateToken(authenticated),
            "Bearer",
            authenticated.getId(),
            authenticated.getNombre(),
            Roles.authority(authenticated.getRol())
        );
    }
}
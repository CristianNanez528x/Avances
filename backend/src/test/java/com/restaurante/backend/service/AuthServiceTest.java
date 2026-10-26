package com.restaurante.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.restaurante.backend.model.Usuario;
import com.restaurante.backend.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final String JWT_SECRET = "bXktY2hhbmdlLW1lLXNlY3JldC1rZXktZm9yLXJlc3RhdXJhbnRlLWp3dA==";

    @Test
    void loginReturnsSignedTokenAndRoleForMatchingPin() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        JwtService jwtService = new JwtService(JWT_SECRET, 60_000);
        Usuario mozo = new Usuario("Mozo", encoder.encode("1234"), "ROLE_MOZO", true);
        ReflectionTestUtils.setField(mozo, "id", 11L);
        when(repository.findByActivoTrue()).thenReturn(List.of(mozo));

        var response = new AuthService(repository, encoder, jwtService).login("1234");

        assertEquals("ROLE_MOZO", response.rol());
        assertEquals("Bearer", response.tokenType());
        assertEquals("11", jwtService.parse(response.token()).getSubject());
        assertTrue(response.token().length() > 20);
    }

    @Test
    void loginRejectsMalformedPinBeforeReadingUsers() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        var service = new AuthService(repository, new BCryptPasswordEncoder(), new JwtService(JWT_SECRET, 60_000));

        assertThrows(IllegalArgumentException.class, () -> service.login("12x4"));
        verifyNoInteractions(repository);
    }
}
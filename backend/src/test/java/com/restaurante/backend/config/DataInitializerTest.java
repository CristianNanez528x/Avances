package com.restaurante.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurante.backend.model.Usuario;
import com.restaurante.backend.repository.MesaRepository;
import com.restaurante.backend.repository.ProductoRepository;
import com.restaurante.backend.repository.UsuarioRepository;
import com.restaurante.backend.utility.Roles;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DataInitializerTest {

    @Test
    void addsDefaultPinAccountOnlyWhenThatRoleDoesNotAlreadyHaveThePin() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        MesaRepository mesas = mock(MesaRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(usuarios.findAllByRolAndActivoTrue(Roles.MOZO)).thenReturn(List.of(
            new Usuario("Mozo existente", encoder.encode("0000"), Roles.MOZO, true)));
        when(usuarios.findAllByRolAndActivoTrue(Roles.COCINERO)).thenReturn(List.of(
            new Usuario("Cocinero existente", encoder.encode("5678"), Roles.COCINERO, true)));
        when(usuarios.findAllByRolAndActivoTrue(Roles.ADMIN)).thenReturn(List.of());
        when(mesas.count()).thenReturn(6L);
        when(productos.count()).thenReturn(8L);

        new DataInitializer().initialize(usuarios, mesas, productos, encoder, "1234", "5678", "9999");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios, org.mockito.Mockito.times(2)).save(captor.capture());
        List<Usuario> created = captor.getAllValues();
        assertEquals(List.of(Roles.MOZO, Roles.ADMIN), created.stream().map(Usuario::getRol).toList());
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches("1234", created.get(0).getPin()));
        org.junit.jupiter.api.Assertions.assertTrue(encoder.matches("9999", created.get(1).getPin()));
    }
}
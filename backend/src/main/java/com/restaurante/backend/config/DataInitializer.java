package com.restaurante.backend.config;

import com.restaurante.backend.model.Mesa;
import com.restaurante.backend.model.Producto;
import com.restaurante.backend.model.Usuario;
import com.restaurante.backend.repository.MesaRepository;
import com.restaurante.backend.repository.ProductoRepository;
import com.restaurante.backend.repository.UsuarioRepository;
import com.restaurante.backend.utility.Roles;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@ConditionalOnProperty(name = "app.seed-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

    @Bean
    ApplicationRunner seedRestaurantData(
        UsuarioRepository usuarioRepository,
        MesaRepository mesaRepository,
        ProductoRepository productoRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.auth.pins.mozo}") String mozoPin,
        @Value("${app.auth.pins.cocinero}") String cocineroPin,
        @Value("${app.auth.pins.admin}") String adminPin
    ) {
        return args -> initialize(usuarioRepository, mesaRepository, productoRepository,
            passwordEncoder, mozoPin, cocineroPin, adminPin);
    }

    @Transactional
    void initialize(
        UsuarioRepository usuarioRepository,
        MesaRepository mesaRepository,
        ProductoRepository productoRepository,
        PasswordEncoder passwordEncoder,
        String mozoPin,
        String cocineroPin,
        String adminPin
    ) {
        createPinUserIfMissing(usuarioRepository, passwordEncoder, "Mozo", mozoPin, Roles.MOZO);
        createPinUserIfMissing(usuarioRepository, passwordEncoder, "Cocinero", cocineroPin, Roles.COCINERO);
        createPinUserIfMissing(usuarioRepository, passwordEncoder, "Administrador", adminPin, Roles.ADMIN);

        if (mesaRepository.count() == 0) {
            mesaRepository.saveAll(List.of(
                new Mesa(1, "LIBRE"), new Mesa(2, "LIBRE"), new Mesa(3, "LIBRE"),
                new Mesa(4, "LIBRE"), new Mesa(5, "LIBRE"), new Mesa(6, "LIBRE")
            ));
        }

        if (productoRepository.count() == 0) {
            productoRepository.saveAll(List.of(
                product("Pollo a la brasa entero", "POLLOS", "42.00"),
                product("Medio pollo", "POLLOS", "24.00"),
                product("Cuarto de pollo", "POLLOS", "15.00"),
                product("Parrilla personal", "PARRILLAS", "32.00"),
                product("Anticuchos (3 unidades)", "PARRILLAS", "18.00"),
                product("Porción de papas fritas", "ACOMPAÑAMIENTOS", "9.00"),
                product("Ensalada de la casa", "ACOMPAÑAMIENTOS", "8.00"),
                product("Chicha morada", "BEBIDAS", "6.00")
            ));
        }
    }

    private void createPinUserIfMissing(
        UsuarioRepository repository,
        PasswordEncoder passwordEncoder,
        String name,
        String rawPin,
        String role
    ) {
        boolean configuredPinExists = repository.findAllByRolAndActivoTrue(role).stream()
            .anyMatch(user -> passwordEncoder.matches(rawPin, user.getPin()));
        if (!configuredPinExists) {
            repository.save(new Usuario(name, passwordEncoder.encode(rawPin), role, true));
        }
    }

    private Producto product(String name, String category, String price) {
        return new Producto(name, category, new BigDecimal(price), null, true);
    }
}
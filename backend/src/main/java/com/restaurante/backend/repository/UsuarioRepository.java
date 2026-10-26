package com.restaurante.backend.repository;

import com.restaurante.backend.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    List<Usuario> findByActivoTrue();
    Optional<Usuario> findByRolAndActivoTrue(String rol);
    List<Usuario> findAllByRolAndActivoTrue(String rol);
    boolean existsByRolAndActivoTrue(String rol);
}
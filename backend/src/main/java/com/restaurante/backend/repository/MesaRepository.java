package com.restaurante.backend.repository;

import com.restaurante.backend.model.Mesa;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    List<Mesa> findAllByOrderByNumeroMesaAsc();
    Optional<Mesa> findByNumeroMesa(Integer numeroMesa);
}
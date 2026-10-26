package com.restaurante.backend.repository;

import com.restaurante.backend.model.Producto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByDisponibleTrueOrderByCategoriaAscNombreAsc();
}
package com.restaurante.backend.repository;

import com.restaurante.backend.model.DetallePedido;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {

    @Query("select coalesce(sum(d.cantidad), 0) from DetallePedido d "
        + "where d.pedido.creadoEn >= :inicio and d.pedido.creadoEn < :fin "
        + "and d.pedido.estado <> 'CANCELADO'")
    Long sumPlatosVendidos(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);
}
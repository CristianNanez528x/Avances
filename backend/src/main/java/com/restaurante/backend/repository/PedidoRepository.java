package com.restaurante.backend.repository;

import com.restaurante.backend.model.Pedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByEstadoInOrderByCreadoEnAsc(List<String> estados);
    List<Pedido> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
    List<Pedido> findAllByOrderByCreadoEnDesc();
    List<Pedido> findByCreadoEnGreaterThanEqualOrderByCreadoEnDesc(LocalDateTime inicio);
    List<Pedido> findByEstadoAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(String estado, LocalDateTime inicio);
    List<Pedido> findByEstadoAndCreadoEnLessThanOrderByCreadoEnDesc(String estado, LocalDateTime fin);
    long countByMesa_IdAndEstadoIn(Long mesaId, List<String> estados);

    @Query("SELECT p FROM Pedido p WHERE p.estado = 'ENTREGADO' ORDER BY p.creadoEn DESC")
    List<Pedido> findAllVentasEntregadas();

    @Query("SELECT p FROM Pedido p WHERE p.creadoEn >= :fechaInicio AND p.creadoEn < :fechaFin "
        + "AND p.estado = 'ENTREGADO' ORDER BY p.creadoEn DESC")
    List<Pedido> obtenerVentasPorRango(
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin
    );

    @Query("SELECT COALESCE(SUM(p.total), 0) FROM Pedido p "
        + "WHERE p.creadoEn >= :fechaInicio AND p.creadoEn < :fechaFin AND p.estado <> 'CANCELADO'")
    BigDecimal sumVentas(
        @Param("fechaInicio") LocalDateTime fechaInicio,
        @Param("fechaFin") LocalDateTime fechaFin
    );
}
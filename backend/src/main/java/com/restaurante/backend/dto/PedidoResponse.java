package com.restaurante.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
    Long id,
    Long mesaId,
    Integer numeroMesa,
    String estado,
    BigDecimal total,
    LocalDateTime creadoEn,
    List<DetallePedidoResponse> detalles
) {
}
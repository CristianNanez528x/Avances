package com.restaurante.backend.dto;

import java.math.BigDecimal;

public record DetallePedidoResponse(
    Long productoId,
    String producto,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal subtotal,
    String observacion
) {
}
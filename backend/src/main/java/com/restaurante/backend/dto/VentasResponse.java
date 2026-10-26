package com.restaurante.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VentasResponse(
    LocalDate fecha,
    BigDecimal dia,
    BigDecimal semana,
    BigDecimal mes
) {
}
package com.restaurante.backend.dto;

import java.math.BigDecimal;

public record ProductoResponse(Long id, String nombre, String categoria, BigDecimal precio, String imagenUrl) {
}
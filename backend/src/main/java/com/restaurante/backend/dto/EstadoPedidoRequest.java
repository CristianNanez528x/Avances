package com.restaurante.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EstadoPedidoRequest(
    @NotBlank(message = "El estado es obligatorio") @Size(max = 24) String estado
) {
}
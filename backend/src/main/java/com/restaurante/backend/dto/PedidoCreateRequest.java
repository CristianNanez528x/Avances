package com.restaurante.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PedidoCreateRequest(
    @NotNull(message = "La mesa es obligatoria") @Positive Long mesaId,
    @NotEmpty(message = "El pedido debe incluir al menos un producto")
    @Size(max = 50, message = "El pedido no puede incluir más de 50 líneas")
    List<@Valid DetalleRequest> detalles
) {
    public record DetalleRequest(
        @NotNull @Positive Long productoId,
        @NotNull @Positive Integer cantidad,
        @Size(max = 500) String observacion
    ) {
    }
}
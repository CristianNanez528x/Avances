package com.restaurante.backend.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.restaurante.backend.dto.CocinaResumenResponse;
import com.restaurante.backend.dto.EstadoPedidoRequest;
import com.restaurante.backend.dto.PedidoResponse;
import com.restaurante.backend.service.PedidoService;
import com.restaurante.backend.service.VentasService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cocina")
public class CocinaController {

    private final PedidoService pedidoService;
    private final VentasService ventasService;
    private final RateLimiter rateLimiter;

    public CocinaController(PedidoService pedidoService, VentasService ventasService, RateLimiter rateLimiter) {
        this.pedidoService = pedidoService;
        this.ventasService = ventasService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/pedidos/pendientes")
    public List<PedidoResponse> pedidosPendientes() {
        enforceRateLimit();
        return pedidoService.pedidosPendientes();
    }

    @PutMapping("/pedidos/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoPedidoRequest request) {
        enforceRateLimit();
        return pedidoService.cambiarEstado(id, request.estado(), false);
    }

    @GetMapping("/resumen")
    public CocinaResumenResponse resumen() {
        enforceRateLimit();
        return ventasService.resumenCocina();
    }

    private void enforceRateLimit() {
        if (!rateLimiter.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
    }
}
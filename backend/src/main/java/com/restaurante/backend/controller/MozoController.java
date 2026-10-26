package com.restaurante.backend.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.restaurante.backend.dto.MesaResponse;
import com.restaurante.backend.dto.PedidoCreateRequest;
import com.restaurante.backend.dto.PedidoResponse;
import com.restaurante.backend.dto.ProductoResponse;
import com.restaurante.backend.model.Mesa;
import com.restaurante.backend.repository.MesaRepository;
import com.restaurante.backend.repository.ProductoRepository;
import com.restaurante.backend.service.PedidoService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/mozo")
public class MozoController {

    private final MesaRepository mesaRepository;
    private final ProductoRepository productoRepository;
    private final PedidoService pedidoService;
    private final RateLimiter rateLimiter;

    public MozoController(
        MesaRepository mesaRepository,
        ProductoRepository productoRepository,
        PedidoService pedidoService,
        RateLimiter rateLimiter
    ) {
        this.mesaRepository = mesaRepository;
        this.productoRepository = productoRepository;
        this.pedidoService = pedidoService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/mesas")
    public List<MesaResponse> mesas() {
        enforceRateLimit();
        return mesaRepository.findAllByOrderByNumeroMesaAsc().stream().map(this::toResponse).toList();
    }

    @GetMapping("/productos")
    public List<ProductoResponse> productos() {
        enforceRateLimit();
        return productoRepository.findByDisponibleTrueOrderByCategoriaAscNombreAsc().stream()
            .map(producto -> new ProductoResponse(producto.getId(), producto.getNombre(), producto.getCategoria(),
                producto.getPrecio(), producto.getImagenUrl()))
            .toList();
    }

    @PostMapping("/pedidos")
    public PedidoResponse crearPedido(
        @AuthenticationPrincipal String usuarioId,
        @Valid @RequestBody PedidoCreateRequest request
    ) {
        enforceRateLimit();
        return pedidoService.crearPedido(Long.valueOf(usuarioId), request);
    }

    @GetMapping("/pedidos/estado")
    public List<PedidoResponse> estadoPedidos(@AuthenticationPrincipal String usuarioId) {
        enforceRateLimit();
        return pedidoService.pedidosDelMozo(Long.valueOf(usuarioId));
    }

    @org.springframework.web.bind.annotation.PutMapping("/pedidos/{id}/estado")
    public PedidoResponse entregarPedido(@org.springframework.web.bind.annotation.PathVariable Long id) {
        enforceRateLimit();
        return pedidoService.cambiarEstado(id, PedidoService.ENTREGADO, true);
    }

    private MesaResponse toResponse(Mesa mesa) {
        return new MesaResponse(mesa.getId(), mesa.getNumeroMesa(), mesa.getEstado());
    }

    private void enforceRateLimit() {
        if (!rateLimiter.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
    }
}
package com.restaurante.backend.service;

import com.restaurante.backend.dto.CocinaResumenResponse;
import com.restaurante.backend.dto.PedidoResponse;
import com.restaurante.backend.dto.VentasResponse;
import com.restaurante.backend.repository.DetallePedidoRepository;
import com.restaurante.backend.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.restaurante.backend.service.PedidoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VentasService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final PedidoService pedidoService;

    public VentasService(
        PedidoRepository pedidoRepository,
        DetallePedidoRepository detallePedidoRepository,
        PedidoService pedidoService
    ) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.pedidoService = pedidoService;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> ventasEntregadas(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new IllegalArgumentException("fechaInicio no puede ser posterior a fechaFin");
        }

        List<com.restaurante.backend.model.Pedido> pedidos;
        if (fechaInicio == null && fechaFin == null) {
            pedidos = pedidoRepository.findAllVentasEntregadas();
        } else if (fechaInicio != null && fechaFin != null) {
            pedidos = pedidoRepository.obtenerVentasPorRango(
                fechaInicio.atStartOfDay(), fechaFin.plusDays(1).atStartOfDay());
        } else if (fechaInicio != null) {
            pedidos = pedidoRepository.findByEstadoAndCreadoEnGreaterThanEqualOrderByCreadoEnDesc(
                PedidoService.ENTREGADO, fechaInicio.atStartOfDay());
        } else {
            pedidos = pedidoRepository.findByEstadoAndCreadoEnLessThanOrderByCreadoEnDesc(
                PedidoService.ENTREGADO, fechaFin.plusDays(1).atStartOfDay());
        }
        return pedidos.stream().map(pedidoService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VentasResponse resumenVentas() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        LocalDateTime nextWeek = weekStart.plusWeeks(1);
        LocalDateTime nextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        return new VentasResponse(
            today,
            nonNull(pedidoRepository.sumVentas(dayStart, tomorrow)),
            nonNull(pedidoRepository.sumVentas(weekStart, nextWeek)),
            nonNull(pedidoRepository.sumVentas(monthStart, nextMonth))
        );
    }

    @Transactional(readOnly = true)
    public CocinaResumenResponse resumenCocina() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<String> estados = List.of(PedidoService.NUEVO, PedidoService.EN_PROCESO, PedidoService.LISTO);
        var pedidosHoy = pedidoRepository.findByCreadoEnGreaterThanEqualOrderByCreadoEnDesc(start).stream()
            .filter(pedido -> pedido.getCreadoEn().isBefore(end))
            .toList();
        long pendientes = pedidosHoy.stream()
            .filter(pedido -> estados.subList(0, 2).contains(pedido.getEstado())).count();
        long listos = pedidosHoy.stream()
            .filter(pedido -> PedidoService.LISTO.equals(pedido.getEstado()) || PedidoService.ENTREGADO.equals(pedido.getEstado()))
            .count();
        Long platos = detallePedidoRepository.sumPlatosVendidos(start, end);
        return new CocinaResumenResponse(today, platos == null ? 0L : platos, pendientes, listos);
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
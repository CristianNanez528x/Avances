package com.restaurante.backend.service;

import com.restaurante.backend.dto.DetallePedidoResponse;
import com.restaurante.backend.dto.PedidoCreateRequest;
import com.restaurante.backend.dto.PedidoResponse;
import com.restaurante.backend.model.DetallePedido;
import com.restaurante.backend.model.Mesa;
import com.restaurante.backend.model.Pedido;
import com.restaurante.backend.model.Producto;
import com.restaurante.backend.model.Usuario;
import com.restaurante.backend.repository.DetallePedidoRepository;
import com.restaurante.backend.repository.MesaRepository;
import com.restaurante.backend.repository.PedidoRepository;
import com.restaurante.backend.repository.ProductoRepository;
import com.restaurante.backend.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    public static final String NUEVO = "PENDIENTE";
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String LISTO = "LISTO";
    public static final String ENTREGADO = "ENTREGADO";
    public static final String CANCELADO = "CANCELADO";

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final DetallePedidoRepository detallePedidoRepository;

    public PedidoService(
        PedidoRepository pedidoRepository,
        MesaRepository mesaRepository,
        ProductoRepository productoRepository,
        UsuarioRepository usuarioRepository,
        DetallePedidoRepository detallePedidoRepository
    ) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
        this.detallePedidoRepository = detallePedidoRepository;
    }

    @Transactional
    public PedidoResponse crearPedido(Long usuarioId, PedidoCreateRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .filter(Usuario::isActivo)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado o inactivo"));
        Mesa mesa = mesaRepository.findById(request.mesaId())
            .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        List<DetallePedido> detalles = request.detalles().stream().map(linea -> {
            Producto producto = productoRepository.findById(linea.productoId())
                .filter(Producto::isDisponible)
                .orElseThrow(() -> new IllegalArgumentException("Producto no disponible: " + linea.productoId()));
            BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(linea.cantidad()))
                .setScale(2, RoundingMode.HALF_UP);
            return new DetallePedido(producto, linea.cantidad(), producto.getPrecio(), subtotal,
                StringUtils.trimToNull(linea.observacion()));
        }).toList();

        BigDecimal total = detalles.stream().map(DetallePedido::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        Pedido pedido = new Pedido(mesa, usuario, NUEVO, total);
        detalles.forEach(pedido::addDetalle);
        mesa.setEstado("OCUPADA");
        mesaRepository.save(mesa);
        return toResponse(pedidoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> pedidosDelMozo(Long usuarioId) {
        return pedidoRepository.findByUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
            .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> pedidosPendientes() {
        return pedidoRepository.findByEstadoInOrderByCreadoEnAsc(List.of(NUEVO, EN_PROCESO)).stream()
            .map(this::toResponse).toList();
    }

    @Transactional
    public PedidoResponse cambiarEstado(Long pedidoId, String estadoSolicitado, boolean esMozo) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado"));
        String estado = StringUtils.upperCase(StringUtils.trimToEmpty(estadoSolicitado)).replace(' ', '_').replace('-', '_');
        boolean transicionValida = switch (estado) {
            case EN_PROCESO -> pedido.getEstado().equals(NUEVO);
            case LISTO -> pedido.getEstado().equals(NUEVO) || pedido.getEstado().equals(EN_PROCESO);
            case ENTREGADO -> esMozo && pedido.getEstado().equals(LISTO);
            case CANCELADO -> !esMozo && (pedido.getEstado().equals(NUEVO) || pedido.getEstado().equals(EN_PROCESO));
            default -> false;
        };
        if (!transicionValida) throw new IllegalArgumentException("Transición de estado no permitida");

        pedido.setEstado(estado);
        Pedido actualizado = pedidoRepository.save(pedido);
        if (ENTREGADO.equals(estado) || CANCELADO.equals(estado)) {
            long pedidosActivos = pedidoRepository.countByMesa_IdAndEstadoIn(
                pedido.getMesa().getId(), List.of(NUEVO, EN_PROCESO, LISTO));
            if (pedidosActivos == 0) {
                Mesa mesa = pedido.getMesa();
                mesa.setEstado("LIBRE");
                mesaRepository.save(mesa);
            }
        }
        return toResponse(actualizado);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> todosLosPedidos() {
        return pedidoRepository.findAllByOrderByCreadoEnDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> pedidosDesde(java.time.LocalDateTime inicio) {
        return pedidoRepository.findByCreadoEnGreaterThanEqualOrderByCreadoEnDesc(inicio).stream()
            .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<Pedido> pedidosParaReporte() {
        return pedidoRepository.findAllByOrderByCreadoEnDesc();
    }

    public PedidoResponse toResponse(Pedido pedido) {
        List<DetallePedidoResponse> detalles = pedido.getDetalles().stream()
            .map(detalle -> new DetallePedidoResponse(
                detalle.getProducto().getId(),
                detalle.getProducto().getNombre(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getSubtotal(),
                detalle.getObservacion()
            )).toList();
        return new PedidoResponse(
            pedido.getId(), pedido.getMesa().getId(), pedido.getMesa().getNumeroMesa(),
            pedido.getEstado(), pedido.getTotal(), pedido.getCreadoEn(), detalles
        );
    }
}
package com.restaurante.backend.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.restaurante.backend.model.Mesa;
import com.restaurante.backend.model.Pedido;
import com.restaurante.backend.model.Usuario;
import com.restaurante.backend.repository.DetallePedidoRepository;
import com.restaurante.backend.repository.MesaRepository;
import com.restaurante.backend.repository.PedidoRepository;
import com.restaurante.backend.repository.ProductoRepository;
import com.restaurante.backend.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PedidoServiceTest {

    @Test
    void waiterCannotMarkAnOrderAsDeliveredBeforeItIsReady() {
        PedidoRepository pedidos = mock(PedidoRepository.class);
        MesaRepository mesas = mock(MesaRepository.class);
        ProductoRepository productos = mock(ProductoRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        DetallePedidoRepository detalles = mock(DetallePedidoRepository.class);
        PedidoService service = new PedidoService(pedidos, mesas, productos, usuarios, detalles);
        Pedido pedido = new Pedido(new Mesa(1, "OCUPADA"), new Usuario("Mozo", "hash", "ROLE_MOZO", true),
            PedidoService.NUEVO, BigDecimal.TEN);
        when(pedidos.findById(7L)).thenReturn(Optional.of(pedido));

        assertThrows(IllegalArgumentException.class,
            () -> service.cambiarEstado(7L, PedidoService.ENTREGADO, true));

        verify(pedidos).findById(7L);
        verifyNoMoreInteractions(pedidos);
    }
}
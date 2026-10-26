package com.restaurante.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.backend.model.Pedido;
import com.restaurante.backend.repository.MesaRepository;
import com.restaurante.backend.repository.PedidoRepository;
import com.restaurante.backend.repository.UsuarioRepository;
import com.restaurante.backend.utility.Roles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void loginIssuesJwtAndRoleProtectsAdminEndpoints() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"9999\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rol").value("ROLE_ADMIN"))
            .andReturn();
        String token = jsonValue(login, "token");

        mockMvc.perform(get("/api/v1/admin/ventas").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        MvcResult waiterLogin = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1234\"}"))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(get("/api/v1/admin/ventas")
                .header("Authorization", "Bearer " + jsonValue(waiterLogin, "token")))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void waiterCanReadSeededTablesWithJwt() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1234\"}"))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(get("/api/v1/mozo/mesas")
                .header("Authorization", "Bearer " + jsonValue(login, "token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(6)));
    }

    @Test
    void invalidPinIsRejectedAndAdminCanDownloadXlsx() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"0000\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"9999\"}"))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult report = mockMvc.perform(get("/api/v1/admin/reportes/excel")
                .header("Authorization", "Bearer " + jsonValue(login, "token")))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn();

        byte[] bytes = report.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 4);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
    }

    @Test
    @Transactional
    void waiterKitchenAndAdminCompleteAnOrderLifecycle() throws Exception {
        MvcResult waiterLogin = login("1234");
        String waiterToken = jsonValue(waiterLogin, "token");

        MvcResult products = mockMvc.perform(get("/api/v1/mozo/productos")
                .header("Authorization", "Bearer " + waiterToken))
            .andExpect(status().isOk())
            .andReturn();
        Long productId = findProductId(products.getResponse().getContentAsString(), "Medio pollo");

        MvcResult createdOrder = mockMvc.perform(post("/api/v1/mozo/pedidos")
                .header("Authorization", "Bearer " + waiterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mesaId\":1,\"detalles\":[{\"productoId\":" + productId
                    + ",\"cantidad\":2,\"observacion\":\"sin piel\"}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.total").value(48.0))
            .andReturn();
        String orderId = jsonValue(createdOrder, "id");

        MvcResult kitchenLogin = login("5678");
        String kitchenToken = jsonValue(kitchenLogin, "token");
        mockMvc.perform(get("/api/v1/cocina/pedidos/pendientes").header("Authorization", "Bearer " + kitchenToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/cocina/pedidos/{id}/estado", orderId)
                .header("Authorization", "Bearer " + kitchenToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/cocina/pedidos/{id}/estado", orderId)
                .header("Authorization", "Bearer " + kitchenToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"LISTO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("LISTO"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/v1/mozo/pedidos/{id}/estado", orderId)
                .header("Authorization", "Bearer " + waiterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ENTREGADO"));
        mockMvc.perform(get("/api/v1/mozo/mesas").header("Authorization", "Bearer " + waiterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].estado").value("LIBRE"));

        MvcResult adminLogin = login("9999");
        mockMvc.perform(get("/api/v1/admin/ventas")
                .header("Authorization", "Bearer " + jsonValue(adminLogin, "token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$[0].estado").value("ENTREGADO"));
    }

    @Test
    @Transactional
    void adminSalesWithoutDatesIncludeHistoricalDeliveredOrders() throws Exception {
        var mesa = mesaRepository.findByNumeroMesa(1).orElseThrow();
        var admin = usuarioRepository.findByRolAndActivoTrue(Roles.ADMIN).orElseThrow();
        Pedido historicalOrder = new Pedido(mesa, admin, "ENTREGADO", BigDecimal.valueOf(45));
        ReflectionTestUtils.setField(historicalOrder, "creadoEn", LocalDateTime.of(2020, 4, 12, 13, 30));
        pedidoRepository.saveAndFlush(historicalOrder);
        Pedido followingDayOrder = new Pedido(mesa, admin, "ENTREGADO", BigDecimal.valueOf(20));
        ReflectionTestUtils.setField(followingDayOrder, "creadoEn", LocalDateTime.of(2020, 4, 13, 0, 0));
        pedidoRepository.saveAndFlush(followingDayOrder);

        String token = jsonValue(login("9999"), "token");
        mockMvc.perform(get("/api/v1/admin/ventas").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$[1].creadoEn").value("2020-04-12T13:30:00"));

        mockMvc.perform(get("/api/v1/admin/ventas")
                .param("fechaInicio", "2020-04-12")
                .param("fechaFin", "2020-04-12")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/v1/admin/ventas")
                .param("fechaInicio", "2021-01-01")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

            @Test
            void corsAllowsTheConfiguredFrontendOrigins() throws Exception {
                mockMvc.perform(options("/api/v1/admin/ventas")
                .header("Origin", "http://127.0.0.1:5500")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"))
                    .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("authorization")));
            }

    private MvcResult login(String pin) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"" + pin + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    }

    private Long findProductId(String productsJson, String productName) throws Exception {
        JsonNode products = objectMapper.readTree(productsJson);
        for (JsonNode product : products) {
            if (productName.equals(product.path("nombre").asText())) return product.path("id").asLong();
        }
        throw new AssertionError("No se encontró el producto " + productName);
    }

    private String jsonValue(MvcResult result, String field) throws Exception {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(result.getResponse().getContentAsString()).get(field).asText();
    }
}
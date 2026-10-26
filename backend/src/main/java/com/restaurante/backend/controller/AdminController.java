package com.restaurante.backend.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.restaurante.backend.dto.PedidoResponse;
import com.restaurante.backend.service.ReporteExcelService;
import com.restaurante.backend.service.VentasService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final VentasService ventasService;
    private final ReporteExcelService reporteExcelService;
    private final RateLimiter rateLimiter;

    public AdminController(VentasService ventasService, ReporteExcelService reporteExcelService, RateLimiter rateLimiter) {
        this.ventasService = ventasService;
        this.reporteExcelService = reporteExcelService;
        this.rateLimiter = rateLimiter;
    }

    @CrossOrigin(origins = {"http://localhost:5500", "http://127.0.0.1:5500", "http://localhost:8001"})
    @GetMapping("/ventas")
    public List<PedidoResponse> ventas(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin
    ) {
        enforceRateLimit();
        return ventasService.ventasEntregadas(fechaInicio, fechaFin);
    }

    @GetMapping("/reportes/excel")
    public ResponseEntity<byte[]> reporteExcel() {
        enforceRateLimit();
        String filename = "ventas-restaurante-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
            .contentType(XLSX_MEDIA_TYPE)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
            .body(reporteExcelService.generarReporte());
    }

    private void enforceRateLimit() {
        if (!rateLimiter.tryAcquire(1, 100, TimeUnit.MILLISECONDS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes");
        }
    }
}
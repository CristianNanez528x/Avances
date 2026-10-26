package com.restaurante.backend.service;

import com.restaurante.backend.model.Pedido;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReporteExcelService {

    private static final List<String> HEADERS = List.of("Pedido", "Fecha", "Mesa", "Estado", "Platos", "Total");
    private final PedidoService pedidoService;

    public ReporteExcelService(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @Transactional(readOnly = true)
    public byte[] generarReporte() {
        List<Pedido> pedidos = pedidoService.pedidosParaReporte();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Ventas");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS.get(column));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Pedido pedido : pedidos) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(pedido.getId());
                row.createCell(1).setCellValue(pedido.getCreadoEn().toString());
                row.createCell(2).setCellValue(pedido.getMesa().getNumeroMesa());
                row.createCell(3).setCellValue(pedido.getEstado());
                row.createCell(4).setCellValue(pedido.getDetalles().stream().mapToInt(detalle -> detalle.getCantidad()).sum());
                row.createCell(5).setCellValue(pedido.getTotal().doubleValue());
            }
            for (int column = 0; column < HEADERS.size(); column++) sheet.autoSizeColumn(column);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible generar el reporte Excel", exception);
        }
    }
}
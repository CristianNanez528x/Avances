package com.restaurante.backend.dto;

import java.time.LocalDate;

public record CocinaResumenResponse(LocalDate fecha, Long platosVendidos, long comandasPendientes, long comandasListas) {
}
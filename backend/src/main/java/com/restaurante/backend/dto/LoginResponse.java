package com.restaurante.backend.dto;

public record LoginResponse(String token, String tokenType, Long usuarioId, String nombre, String rol) {
}
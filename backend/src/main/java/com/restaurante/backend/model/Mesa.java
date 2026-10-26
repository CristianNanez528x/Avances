package com.restaurante.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mesas")
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_mesa", nullable = false, unique = true)
    private Integer numeroMesa;

    @Column(nullable = false, length = 20)
    private String estado;

    protected Mesa() {
    }

    public Mesa(Integer numeroMesa, String estado) {
        this.numeroMesa = numeroMesa;
        this.estado = estado;
    }

    public Long getId() { return id; }
    public Integer getNumeroMesa() { return numeroMesa; }
    public void setNumeroMesa(Integer numeroMesa) { this.numeroMesa = numeroMesa; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
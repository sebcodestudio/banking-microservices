package com.banco.creditservice.controller;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload para registrar un pago o un consumo. {@code payerCustomerId} es
 * opcional y solo aplica a pagos (Fase 10): permite que un cliente pague
 * el credito de un tercero. Si se omite, se asume que paga el propio
 * titular del credito.
 */
public record MovementRequest(

        @NotNull
        @Positive
        BigDecimal amount,

        String payerCustomerId) {
}

package com.banco.accountservice.controller;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload para transferir fondos desde la cuenta de la ruta hacia
 * {@code destinationAccountId} (Fase 9). La cuenta destino puede
 * pertenecer al mismo cliente o a un tercero, siempre dentro del mismo
 * banco (misma coleccion de cuentas).
 */
public record TransferRequest(

        @NotBlank
        String destinationAccountId,

        @NotNull
        @Positive
        BigDecimal amount) {
}

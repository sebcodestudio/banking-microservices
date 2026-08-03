package com.banco.yankiservice.controller;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Payload para una carga o un retiro de saldo hacia la cuenta principal vinculada. */
public record AmountRequest(

        @NotNull
        @Positive
        BigDecimal amount) {
}

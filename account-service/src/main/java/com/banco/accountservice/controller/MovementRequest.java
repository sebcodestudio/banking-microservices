package com.banco.accountservice.controller;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Payload para registrar un deposito o un retiro. */
public record MovementRequest(

        @NotNull
        @Positive
        BigDecimal amount) {
}

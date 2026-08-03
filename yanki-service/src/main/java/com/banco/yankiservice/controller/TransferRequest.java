package com.banco.yankiservice.controller;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Payload para enviar saldo de un monedero a otro por numero de celular. */
public record TransferRequest(

        @NotBlank
        String senderPhone,

        @NotBlank
        String receiverPhone,

        @NotNull
        @Positive
        BigDecimal amount) {
}

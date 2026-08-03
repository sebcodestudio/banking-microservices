package com.banco.yankiservice.controller;

import jakarta.validation.constraints.NotBlank;

/** Payload para asociar el monedero a una tarjeta de debito existente en account-service. */
public record LinkDebitCardRequest(

        @NotBlank
        String debitCardId) {
}

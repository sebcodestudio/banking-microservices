package com.banco.customerservice.controller;

import jakarta.validation.constraints.NotBlank;

/** Payload de login (Fase 13). */
public record LoginRequest(

        @NotBlank
        String username,

        @NotBlank
        String password) {
}

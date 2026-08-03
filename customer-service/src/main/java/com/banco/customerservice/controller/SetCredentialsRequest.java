package com.banco.customerservice.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload para registrar o reemplazar las credenciales de un cliente existente (Fase 13). */
public record SetCredentialsRequest(

        @NotBlank
        String username,

        @NotBlank
        @Size(min = 8)
        String password) {
}

package com.banco.customerservice.service;

/** Respuesta del login (Fase 13): el JWT emitido y su vigencia. */
public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
}

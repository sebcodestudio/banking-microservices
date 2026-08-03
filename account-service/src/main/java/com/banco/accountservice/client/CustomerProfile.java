package com.banco.accountservice.client;

/**
 * Perfil comercial del cliente, tal como lo expone customer-service. Se
 * replica aqui (en vez de compartir codigo entre microservicios) para
 * mantener a account-service desacoplado de customer-service.
 */
public enum CustomerProfile {
    STANDARD,
    VIP,
    PYME
}

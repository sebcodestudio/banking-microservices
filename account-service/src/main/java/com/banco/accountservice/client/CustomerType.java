package com.banco.accountservice.client;

/**
 * Tipo de cliente, tal como lo expone customer-service. Se replica aqui
 * (en vez de compartir codigo entre microservicios) para mantener a
 * account-service desacoplado de customer-service.
 */
public enum CustomerType {
    PERSONAL,
    BUSINESS
}

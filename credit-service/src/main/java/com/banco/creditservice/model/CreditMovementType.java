package com.banco.creditservice.model;

/**
 * Tipo de movimiento realizado sobre un producto de credito.
 */
public enum CreditMovementType {
    /** Pago aplicado a la deuda de un credito personal, empresarial o de tarjeta. */
    PAYMENT,
    /** Consumo cargado a una tarjeta de credito. */
    CONSUMPTION
}

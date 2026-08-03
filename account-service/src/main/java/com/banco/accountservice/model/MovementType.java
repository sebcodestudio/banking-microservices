package com.banco.accountservice.model;

/**
 * Tipo de movimiento realizado sobre una cuenta bancaria.
 */
public enum MovementType {
    DEPOSIT,
    WITHDRAWAL,
    /** Comision cobrada por exceder el numero de transacciones sin costo del mes (Fase 8). */
    FEE,
    /** Salida de fondos en la cuenta origen de una transferencia (Fase 9). */
    TRANSFER_OUT,
    /** Entrada de fondos en la cuenta destino de una transferencia (Fase 9). */
    TRANSFER_IN,
    /** Pago realizado con una tarjeta de debito contra su cuenta vinculada (Fase 11). */
    DEBIT_CARD_PAYMENT
}

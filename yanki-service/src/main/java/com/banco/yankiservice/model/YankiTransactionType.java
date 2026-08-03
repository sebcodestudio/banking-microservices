package com.banco.yankiservice.model;

/**
 * Tipo de movimiento sobre un monedero Yanki (Fase 12).
 */
public enum YankiTransactionType {
    /** Envio de saldo a otro monedero por numero de celular. */
    SEND,
    /** Recepcion de saldo desde otro monedero por numero de celular. */
    RECEIVE,
    /** Carga de saldo desde la cuenta bancaria vinculada a la tarjeta de debito. */
    LOAD,
    /** Retiro de saldo hacia la cuenta bancaria vinculada a la tarjeta de debito. */
    WITHDRAW
}

package com.banco.yankiservice.model;

/**
 * Tipo de operacion asincrona (via Kafka) entre yanki-service y
 * account-service (Fase 12).
 */
public enum YankiOperationType {
    /** Asocia el monedero a una tarjeta de debito existente en account-service. */
    LINK_CARD,
    /** Acredita saldo al monedero, debitando la cuenta principal vinculada. */
    CREDIT,
    /** Debita saldo del monedero, acreditando la cuenta principal vinculada. */
    DEBIT
}

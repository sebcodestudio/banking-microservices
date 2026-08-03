package com.banco.accountservice.kafka;

/**
 * Copia local (Fase 12) del tipo de operacion Yanki definido en
 * yanki-service; cada microservicio mantiene su propia copia del
 * contrato del evento (mismo patron que los DTO de cliente ya
 * duplicados entre account-service y credit-service), sin depender de
 * una libreria compartida.
 */
public enum YankiOperationType {
    LINK_CARD,
    CREDIT,
    DEBIT
}

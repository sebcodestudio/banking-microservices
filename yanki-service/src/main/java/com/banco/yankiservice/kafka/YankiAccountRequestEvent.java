package com.banco.yankiservice.kafka;

import java.math.BigDecimal;

import com.banco.yankiservice.model.YankiOperationType;

/**
 * Evento publicado en el topico {@code yanki.account.requests} (Fase 12)
 * para vincular una tarjeta de debito o mover saldo entre un monedero
 * Yanki y la cuenta bancaria vinculada a esa tarjeta. Copia local del
 * contrato tambien definido en account-service (mismo patron que los DTO
 * de cliente ya duplicados entre servicios REST existentes).
 */
public record YankiAccountRequestEvent(
        String correlationId,
        String walletId,
        YankiOperationType operationType,
        String debitCardId,
        String accountId,
        BigDecimal amount) {
}

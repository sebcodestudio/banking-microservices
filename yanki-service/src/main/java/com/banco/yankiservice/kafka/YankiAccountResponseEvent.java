package com.banco.yankiservice.kafka;

import java.math.BigDecimal;

import com.banco.yankiservice.model.YankiOperationType;

/**
 * Evento consumido desde el topico {@code yanki.account.responses}
 * (Fase 12), publicado por account-service en respuesta a un
 * {@link YankiAccountRequestEvent}.
 */
public record YankiAccountResponseEvent(
        String correlationId,
        String walletId,
        YankiOperationType operationType,
        boolean success,
        String accountId,
        BigDecimal newAccountBalance,
        String errorMessage) {
}

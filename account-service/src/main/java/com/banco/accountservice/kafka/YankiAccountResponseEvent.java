package com.banco.accountservice.kafka;

import java.math.BigDecimal;

/**
 * Evento publicado por account-service en el topico {@code yanki.account.responses}
 * (Fase 12) en respuesta a un {@link YankiAccountRequestEvent}: confirma o
 * rechaza la operacion solicitada.
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

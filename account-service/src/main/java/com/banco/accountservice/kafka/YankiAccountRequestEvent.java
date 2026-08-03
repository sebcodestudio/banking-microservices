package com.banco.accountservice.kafka;

import java.math.BigDecimal;

/**
 * Evento publicado por yanki-service en el topico {@code yanki.account.requests}
 * (Fase 12): solicita vincular una tarjeta de debito o mover saldo entre
 * un monedero Yanki y la cuenta bancaria vinculada a esa tarjeta.
 * {@code debitCardId} solo aplica a {@code LINK_CARD}; {@code accountId}
 * y {@code amount} solo aplican a {@code CREDIT}/{@code DEBIT}.
 */
public record YankiAccountRequestEvent(
        String correlationId,
        String walletId,
        YankiOperationType operationType,
        String debitCardId,
        String accountId,
        BigDecimal amount) {
}

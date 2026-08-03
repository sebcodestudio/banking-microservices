package com.banco.yankiservice.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Movimiento (envio, recepcion, carga o retiro) sobre un monedero Yanki
 * (Fase 12).
 */
@Document(collection = "yanki_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YankiTransaction {

    @Id
    private String id;

    @NotBlank
    private String walletId;

    @NotNull
    private YankiTransactionType type;

    @NotNull
    @Positive
    private BigDecimal amount;

    /** Numero de celular de la contraparte; solo aplica a SEND/RECEIVE. */
    private String counterpartyPhone;

    @NotNull
    private Instant movementDate;

    @CreatedDate
    private Instant createdAt;
}

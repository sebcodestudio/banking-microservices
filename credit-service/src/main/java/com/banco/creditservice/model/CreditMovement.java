package com.banco.creditservice.model;

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
 * Movimiento (pago o consumo) realizado sobre un producto de credito.
 */
@Document(collection = "credit_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditMovement {

    @Id
    private String id;

    @NotBlank
    private String creditId;

    @NotNull
    private CreditMovementType movementType;

    @NotNull
    @Positive
    private BigDecimal amount;

    /** Balance del credito inmediatamente despues de aplicar este movimiento. */
    @NotNull
    private BigDecimal balanceAfter;

    @NotNull
    private Instant movementDate;

    /**
     * Id del cliente que realizo el pago (Fase 10). Puede ser distinto del
     * titular del credito (pago de un producto de credito de un tercero);
     * si no se especifica al pagar, se completa con el titular. No aplica
     * a movimientos de tipo {@code CONSUMPTION}, que quedan con este campo
     * en null.
     */
    private String payerCustomerId;

    @CreatedDate
    private Instant createdAt;
}

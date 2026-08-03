package com.banco.accountservice.model;

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
 * Movimiento (deposito o retiro) realizado sobre una cuenta bancaria.
 */
@Document(collection = "account_movements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountMovement {

    @Id
    private String id;

    @NotBlank
    private String accountId;

    @NotNull
    private MovementType movementType;

    @NotNull
    @Positive
    private BigDecimal amount;

    /** Saldo de la cuenta inmediatamente despues de aplicar este movimiento. */
    @NotNull
    private BigDecimal balanceAfter;

    @NotNull
    private Instant movementDate;

    @CreatedDate
    private Instant createdAt;
}

package com.banco.yankiservice.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro del estado de una operacion asincrona iniciada por
 * yanki-service y resuelta por account-service via Kafka (Fase 12): el
 * endpoint REST que la dispara responde {@code 202 Accepted} de
 * inmediato, y el cliente consulta {@code GET /api/yanki/operations/{correlationId}}
 * para conocer el resultado una vez que llega el evento de respuesta.
 *
 * <p>El constructor con todos los argumentos es {@code package-private},
 * consistente con el resto de entidades del proyecto (ver {@code Customer}):
 * evita que Jackson lo trate como creador implicito y pise con {@code null}
 * los valores por defecto de campos como {@code status} cuando se
 * deserializa un JSON que no los incluye.</p>
 */
@Document(collection = "yanki_operations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class YankiOperation {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String correlationId;

    @NotBlank
    private String walletId;

    @NotNull
    private YankiOperationType operationType;

    /** Solo para {@code LINK_CARD}: id de la tarjeta de debito que se intenta vincular. */
    private String debitCardId;

    /** Solo para {@code CREDIT}/{@code DEBIT}: monto a mover, guardado aqui porque la respuesta de account-service no lo repite. */
    private BigDecimal amount;

    @NotNull
    private YankiOperationStatus status = YankiOperationStatus.PENDING;

    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

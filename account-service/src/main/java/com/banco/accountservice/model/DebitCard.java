package com.banco.accountservice.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
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
 * Tarjeta de debito asociada a una cuenta bancaria (Fase 11, Parte III).
 * Los pagos con la tarjeta se cargan directamente a {@code accountId}; una
 * cuenta admite como maximo una tarjeta de debito activa.
 *
 * <p>El constructor con todos los argumentos es {@code package-private} a
 * proposito: con {@code jackson-module-parameter-names} activo, Jackson
 * trata un unico constructor <b>publico</b> con argumentos como creador
 * implicito, pasando {@code null} a cualquier propiedad ausente del JSON
 * del request (por ejemplo {@code status}) y pisando el valor por defecto
 * del campo. Al no ser publico, Jackson vuelve a usar el constructor sin
 * argumentos + setters, que si respeta esos valores por defecto.</p>
 */
@Document(collection = "debit_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class DebitCard {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String cardNumber;

    /** Id de la cuenta bancaria (account-service) a la que se cargan los pagos. */
    @NotBlank
    private String accountId;

    /** Id del cliente titular, tal como lo expone customer-service; debe coincidir con el titular de la cuenta. */
    @NotBlank
    private String customerId;

    @NotNull
    private DebitCardStatus status = DebitCardStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;
}

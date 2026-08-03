package com.banco.creditservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Producto de credito (producto activo). Clase base para los tres tipos
 * soportados: {@link PersonalCredit}, {@link BusinessCredit} y
 * {@link CreditCard}. Los tres comparten la coleccion "credits",
 * discriminados por Spring Data a traves del campo interno _class.
 *
 * <p>El campo {@code balance} tiene un significado distinto segun el tipo
 * de producto: para {@link PersonalCredit} y {@link BusinessCredit}
 * representa la deuda pendiente (disminuye con los pagos); para
 * {@link CreditCard} representa el consumo acumulado (aumenta con los
 * consumos y disminuye con los pagos).</p>
 *
 * <p>El constructor con todos los argumentos es {@code package-private} a
 * proposito: con {@code jackson-module-parameter-names} activo, Jackson
 * trata un unico constructor <b>publico</b> con argumentos como creador
 * implicito, pasando {@code null} a cualquier propiedad ausente del JSON
 * del request (por ejemplo {@code balance}/{@code status}) y pisando el
 * valor por defecto del campo. Al no ser publico, Jackson vuelve a usar el
 * constructor sin argumentos + setters, que si respeta esos valores por
 * defecto.</p>
 */
@Document(collection = "credits")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "creditType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PersonalCredit.class, name = "PERSONAL"),
        @JsonSubTypes.Type(value = BusinessCredit.class, name = "BUSINESS"),
        @JsonSubTypes.Type(value = CreditCard.class, name = "CREDIT_CARD")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
public abstract class CreditProduct {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String creditNumber;

    /** Id del cliente (customer-service) titular del credito. */
    @NotBlank
    private String customerId;

    @NotNull
    private BigDecimal balance = BigDecimal.ZERO;

    @NotBlank
    private String currency;

    @NotNull
    private CreditStatus status = CreditStatus.ACTIVE;

    /** Si se omite, el service la completa con la fecha actual. */
    private LocalDate openingDate;

    /**
     * Fecha limite de la proxima cuota (Fase 10). Si se omite al otorgar
     * el credito, el service la completa con un mes a partir de hoy; cada
     * pago que no salda el balance la adelanta un mes, y se limpia cuando
     * el credito queda saldado. Un credito esta "vencido" cuando tiene
     * balance pendiente y esta fecha ya paso (ver
     * {@code CreditProductRepository.existsByCustomerIdAndBalanceGreaterThanAndNextPaymentDueDateBefore}).
     */
    private LocalDate nextPaymentDueDate;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

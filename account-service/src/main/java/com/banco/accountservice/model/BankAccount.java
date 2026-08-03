package com.banco.accountservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Cuenta bancaria (producto pasivo). Clase base para los tres tipos de
 * cuenta soportados: {@link SavingsAccount}, {@link CheckingAccount} y
 * {@link FixedTermAccount}. Las tres comparten la coleccion "accounts",
 * discriminadas por Spring Data a traves del campo interno _class.
 *
 * <p>El constructor con todos los argumentos es {@code package-private} a
 * proposito: con {@code jackson-module-parameter-names} activo, Jackson
 * trata un unico constructor <b>publico</b> con argumentos como creador
 * implicito, pasando {@code null} a cualquier propiedad ausente del JSON
 * del request (por ejemplo {@code status}) y pisando el valor por defecto
 * del campo. Al no ser publico, Jackson vuelve a usar el constructor sin
 * argumentos + setters, que si respeta esos valores por defecto.</p>
 */
@Document(collection = "accounts")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "accountType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SavingsAccount.class, name = "SAVINGS"),
        @JsonSubTypes.Type(value = CheckingAccount.class, name = "CHECKING"),
        @JsonSubTypes.Type(value = FixedTermAccount.class, name = "FIXED_TERM")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
public abstract class BankAccount {

    @Id
    private String id;

    @NotBlank
    @Indexed(unique = true)
    private String accountNumber;

    /** Id del cliente (customer-service) dueño principal de la cuenta. */
    @NotBlank
    private String customerId;

    @NotNull
    private BigDecimal balance = BigDecimal.ZERO;

    @NotBlank
    private String currency;

    /**
     * Monto minimo requerido para abrir la cuenta (Fase 8); puede ser
     * cero. Se valida contra el {@code balance} inicial indicado al
     * crear la cuenta.
     */
    @NotNull
    @PositiveOrZero
    private BigDecimal minimumOpeningAmount = BigDecimal.ZERO;

    /**
     * Cantidad de transacciones (depositos + retiros) que no cobran
     * comision dentro de un mismo mes (Fase 8). Las transacciones que
     * excedan este numero se cobran segun {@link #transactionFeeAmount}.
     * No sustituye los limites propios de cada tipo de cuenta (por
     * ejemplo, el limite duro de movimientos de la cuenta de ahorro o el
     * unico movimiento permitido de la cuenta a plazo fijo).
     */
    @NotNull
    @PositiveOrZero
    private Integer freeMonthlyTransactionLimit;

    /** Comision cobrada por cada transaccion que exceda el limite gratuito (Fase 8); puede ser cero. */
    @NotNull
    @PositiveOrZero
    private BigDecimal transactionFeeAmount;

    @NotNull
    private AccountStatus status = AccountStatus.ACTIVE;

    /** Si se omite, el service la completa con la fecha actual. */
    private LocalDate openingDate;

    /**
     * Ids de clientes titulares. Una cuenta personal tiene un unico titular;
     * una cuenta corriente empresarial puede tener uno o mas. Si se omite,
     * el service la completa con [customerId] como unico titular.
     */
    private List<String> holders;

    /**
     * Ids de clientes firmantes autorizados. Solo aplica a cuentas
     * corrientes empresariales (cero o mas); en el resto de casos queda vacia.
     */
    private List<String> authorizedSigners;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

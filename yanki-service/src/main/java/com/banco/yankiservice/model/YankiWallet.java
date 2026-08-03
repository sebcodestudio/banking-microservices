package com.banco.yankiservice.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Monedero movil Yanki (Fase 12, Parte III). No requiere ser cliente del
 * banco: solo un numero de documento de identidad, numero de celular
 * (usado para enviar/recibir pagos), IMEI y correo electronico.
 *
 * <p>{@code linkedDebitCardId}/{@code linkedAccountId} quedan completos
 * solo despues de un vinculo exitoso con una tarjeta de debito de
 * account-service (via Kafka, ver {@code YankiOperation}); mientras no
 * haya vinculo, cargar o retirar saldo hacia una cuenta bancaria no es
 * posible.</p>
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
@Document(collection = "yanki_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class YankiWallet {

    @Id
    private String id;

    @NotNull
    private DocumentType documentType;

    @NotBlank
    @Indexed(unique = true)
    private String documentNumber;

    @NotBlank
    @Indexed(unique = true)
    private String phoneNumber;

    @NotBlank
    private String imei;

    @NotBlank
    @Email
    private String email;

    @NotNull
    private BigDecimal balance = BigDecimal.ZERO;

    /** Id de la tarjeta de debito vinculada (account-service), null hasta que el vinculo se confirme por Kafka. */
    private String linkedDebitCardId;

    /** Id de la cuenta bancaria principal asociada a esa tarjeta; unico destino valido de carga/retiro. */
    private String linkedAccountId;

    @NotNull
    private YankiWalletStatus status = YankiWalletStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

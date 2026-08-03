package com.banco.customerservice.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cliente del banco. Un mismo documento representa tanto a un cliente
 * personal como a uno empresarial; el campo {@code customerType} indica
 * cual es el caso, y solo se completan los campos correspondientes a ese tipo.
 *
 * <p>El constructor con todos los argumentos es {@code package-private} a
 * proposito (lo sigue necesitando {@code @Builder} internamente): con
 * {@code jackson-module-parameter-names} activo (registrado por defecto en
 * Spring Boot), Jackson trata un unico constructor <b>publico</b> con
 * argumentos como creador implicito y lo preferiria sobre el constructor
 * sin argumentos mas los setters, pasando {@code null} en cualquier
 * propiedad ausente del JSON del request y pisando asi los valores por
 * defecto de {@link #status}/{@link #profile}. Al no ser publico, Jackson
 * lo ignora y vuelve a deserializar via el constructor sin argumentos +
 * setters, que si respeta esos valores por defecto.</p>
 */
@Document(collection = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Customer {

    @Id
    private String id;

    @NotNull
    private CustomerType customerType;

    /** Numero de documento de identidad (DNI, carnet de extranjeria, etc.). */
    @NotBlank
    @Indexed(unique = true)
    private String documentNumber;

    @NotBlank
    @Email
    private String email;

    private String phoneNumber;

    private String address;

    private CustomerStatus status = CustomerStatus.ACTIVE;

    /**
     * Credenciales de acceso (Fase 13, Parte III), opcionales: sin ellas
     * el cliente sigue existiendo como registro de negocio, pero no puede
     * loguearse en {@code POST /api/auth/login}. {@code passwordHash}
     * nunca se expone en las respuestas (ver {@code @JsonIgnore} en el
     * getter) ni se acepta en actualizaciones que no pasen por el flujo
     * de login/registro de credenciales.
     */
    @Indexed(unique = true, sparse = true)
    private String username;

    @JsonIgnore
    private String passwordHash;

    /**
     * Perfil comercial del cliente (Fase 8). Determina beneficios y
     * requisitos adicionales al abrir ciertas cuentas (ver {@link CustomerProfile}).
     * Validado en el service: VIP solo para PERSONAL, PYME solo para BUSINESS.
     */
    @NotNull
    private CustomerProfile profile = CustomerProfile.STANDARD;

    // Campos exclusivos de cliente personal (customerType = PERSONAL)
    private String firstName;
    private String lastName;
    private LocalDate birthDate;

    // Campos exclusivos de cliente empresarial (customerType = BUSINESS)
    private String businessName;
    private String ruc;
    private String legalRepresentativeName;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

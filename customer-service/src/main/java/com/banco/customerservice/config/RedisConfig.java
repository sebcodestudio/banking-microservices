package com.banco.customerservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.banco.customerservice.model.Customer;

/**
 * Cliente Redis reactivo tipado para cachear {@link Customer} (Fase 14,
 * Parte III): claves como texto plano, valores serializados a JSON
 * (Jackson) para que sean legibles/inspeccionables en Redis, a diferencia
 * de la serializacion Java por defecto.
 */
@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Customer> customerRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        // Se reutiliza el ObjectMapper autoconfigurado de Spring Boot (trae
        // jackson-datatype-jsr310 registrado); Jackson2JsonRedisSerializer(Class)
        // crea uno propio sin ese modulo y falla al serializar LocalDate/Instant.
        Jackson2JsonRedisSerializer<Customer> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Customer.class);
        RedisSerializationContext<String, Customer> context = RedisSerializationContext
                .<String, Customer>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(connectionFactory, context);
    }
}

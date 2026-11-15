package com.lifeplanner.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Completes the strict-input policy of application.yml: {@code allow-coercion-of-scalars: false} stops "20" → number,
 * but Jackson would still turn a JSON number or boolean into text ({@code "title": 123} → "123"). Text fields accept
 * only JSON strings (or null), consistently for POST bodies and PATCH bodies.
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer textFieldsAcceptOnlyStrings() {
        return builder -> builder.postConfigurer(mapper -> mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}

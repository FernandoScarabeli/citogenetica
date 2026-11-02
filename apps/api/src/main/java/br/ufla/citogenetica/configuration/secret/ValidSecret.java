package br.ufla.citogenetica.configuration.secret;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ValidSecretValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSecret {

    String variable();

    int minimumBytes() default 1;

    int maximumBytes() default Integer.MAX_VALUE;

    String message() default "{variable} deve ter no mínimo {minimumBytes} bytes e não pode ser um placeholder conhecido.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

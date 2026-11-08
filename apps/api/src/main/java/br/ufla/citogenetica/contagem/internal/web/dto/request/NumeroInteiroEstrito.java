package br.ufla.citogenetica.contagem.internal.web.dto.request;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * Desserializadores que só aceitam número JSON inteiro. O Jackson, por padrão, aceita {@code 1.5} ou
 * {@code "5"} num campo inteiro (truncando ou convertendo); aqui isso vira corpo ilegível (400).
 * Valor fora do intervalo do tipo também é recusado.
 */
public final class NumeroInteiroEstrito {

    private NumeroInteiroEstrito() {
    }

    public static class Inteiro extends StdDeserializer<Integer> {

        public Inteiro() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return (Integer) contexto.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }

    public static class Longo extends StdDeserializer<Long> {

        public Longo() {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser parser, DeserializationContext contexto) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return (Long) contexto.handleUnexpectedToken(Long.class, parser);
            }
            return parser.getLongValue();
        }
    }
}

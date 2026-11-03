package br.ufla.citogenetica.shared.filter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;

import jakarta.persistence.criteria.*;

public class CriteriaCreator {

    private static final Set<String> PARAMETROS_RESERVADOS = Set.of("page", "size", "sort", "sortBy", "sortDirection");
    private static final Set<String> OPERADORES = Set.of("eq", "ne", "gt", "gte", "lt", "lte");
    private static final char ESCAPE_LIKE = '\\';

    /**
     * Monta a Specification de filtros a partir dos parâmetros da requisição. Campos fora de
     * {@code campos} geram {@link FiltroInvalidoException} em vez de serem ignorados.
     */
    public static <T> Specification<T> byFilterMap(MultiValueMap<String, String> params,
                                                   CamposPermitidos campos) {
        Map<String, List<Object>> filterMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            List<Object> values = new ArrayList<>(entry.getValue());
            filterMap.put(entry.getKey(), values);
        }

        filterMap.keySet().removeAll(PARAMETROS_RESERVADOS);
        filterMap.keySet().forEach(chave -> validarCamposDeFiltro(campos, chave));

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate finalPredicate = cb.conjunction();
            List<Predicate> orPredicates = new ArrayList<>();

            for (Map.Entry<String, List<Object>> entry : filterMap.entrySet()) {
                String key = entry.getKey();
                List<Object> values = entry.getValue();

                if (values != null && !values.isEmpty()) {
                    if (key.contains("!")) {
                        String[] orKeys = key.split("!");
                        List<Predicate> currentOrPredicates = new ArrayList<>();

                        for (String orKey : orKeys) {
                            orKey = orKey.trim();

                            boolean isJsonField = orKey.startsWith("dto.");

                            Predicate predicate;
                            if (isJsonField) {
                                String jsonPath = orKey.substring(4);
                                predicate = createJsonbPredicate(root, cb, jsonPath, values.get(0));
                            } else {
                                Path<?> path = navigatePath(root, orKey);

                                List<Object> convertedValues = new ArrayList<>();
                                for (Object value : values) {
                                    convertedValues.add(convertValueToFieldType(path, value));
                                }

                                // Extrai o nome do campo do orKey
                                String fieldName = orKey.contains(".") ? orKey.substring(orKey.lastIndexOf(".") + 1) : orKey;
                                predicate = createPredicate(cb, path, convertedValues, fieldName);
                            }
                            currentOrPredicates.add(predicate);
                        }

                        orPredicates.add(cb.or(currentOrPredicates.toArray(new Predicate[0])));
                    } else {
                        Predicate predicate = createPredicateForEntry(root, cb, key, values);
                        finalPredicate = cb.and(finalPredicate, predicate);
                    }
                }
            }

            if (!orPredicates.isEmpty()) {
                finalPredicate = cb.and(finalPredicate, cb.or(orPredicates.toArray(new Predicate[0])));
            }

            return finalPredicate;
        };
    }

    private static void validarCamposDeFiltro(CamposPermitidos campos, String chave) {
        for (String campo : chave.split("!")) {
            String nome = campo.trim();
            if (!campos.filtro().contains(nome)) {
                throw new FiltroInvalidoException("Campo de filtro não permitido: '" + nome + "'.");
            }
        }
    }

    /** Escapa os curingas do LIKE para que o valor digitado seja buscado literalmente. */
    private static String padraoContem(String valor) {
        String normalizado = removeAccents(valor.toLowerCase());
        StringBuilder escapado = new StringBuilder("%");
        for (char caractere : normalizado.toCharArray()) {
            if (caractere == '%' || caractere == '_' || caractere == ESCAPE_LIKE) {
                escapado.append(ESCAPE_LIKE);
            }
            escapado.append(caractere);
        }
        return escapado.append('%').toString();
    }

    private static <T> Predicate createPredicateForEntry(Root<T> root, CriteriaBuilder cb, String key,
                                                         List<Object> values) {
        if (key.startsWith("dto.")) {
            String jsonPath = key.substring(4);
            List<Predicate> jsonPredicates = new ArrayList<>();
            for (Object value : values) {
                jsonPredicates.add(createJsonbPredicate(root, cb, jsonPath, value));
            }
            return cb.and(jsonPredicates.toArray(new Predicate[0]));
        } else {
            Path<?> path = navigatePath(root, key);

            List<Object> convertedValues = new ArrayList<>();
            for (Object value : values) {
                convertedValues.add(convertValueToFieldType(path, value));
            }

            // Extrai o nome do campo do key (última parte após o ponto)
            String fieldName = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1) : key;
            return createPredicate(cb, path, convertedValues, fieldName);
        }
    }

    private static Expression<String> getUnaccentLowerExpression(CriteriaBuilder cb, Path<?> path) {
        return cb.function("unaccent", String.class, cb.function("lower", String.class, path.as(String.class)));
    }

    private static <T> Predicate createJsonbPredicate(Root<T> root, CriteriaBuilder cb, String jsonPath, Object value) {
        Expression<String> jsonExpr = buildJsonbExtractPath(root, cb, jsonPath);
        Expression<String> unaccentLowerExpr = cb.function("unaccent", String.class,
                cb.function("lower", String.class, jsonExpr));

        return cb.like(unaccentLowerExpr, padraoContem(value.toString()), ESCAPE_LIKE);
    }

    private static <T> Expression<String> buildJsonbExtractPath(Root<T> root, CriteriaBuilder cb, String jsonPath) {
        Path<?> dtoPath = root.get("dto");
        String[] pathParts = jsonPath.split("\\.");
        Expression<?>[] args = new Expression[pathParts.length + 1];
        args[0] = dtoPath;

        for (int i = 0; i < pathParts.length; i++) {
            args[i + 1] = cb.literal(pathParts[i]);
        }

        return cb.function("jsonb_extract_path_text", String.class, args);
    }

    private static <T> Path<?> navigatePath(Root<T> root, String key) {
        Path<?> path = root;
        if (key.contains(".")) {
            String[] parts = key.split("\\.");
            for (int i = 0; i < parts.length - 1; i++) {
                path = path.get(parts[i]);
            }
            key = parts[parts.length - 1];
        }
        return path.get(key);
    }

    private static String removeAccents(String value) {
        String normalizer = Normalizer.normalize(value, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalizer).replaceAll("");
    }

    private static Predicate createPredicate(CriteriaBuilder cb, Path<?> path, List<Object> values) {
        return createPredicate(cb, path, values, null);
    }

    private static Predicate createPredicate(CriteriaBuilder cb, Path<?> path, List<Object> values, String fieldName) {

        if (isDateType(path)) {
            boolean hasDateOperators = values.stream().anyMatch(val -> val.toString().contains(":"));

            if (hasDateOperators) {
                List<String> stringValues = new ArrayList<>();
                for (Object value : values) {
                    stringValues.add(value.toString());
                }
                return createDatePredicate(cb, path, stringValues);
            }
        }

        if (isNumericType(path)) {
            boolean hasNumericOperators = values.stream().anyMatch(val -> val.toString().contains(":"));
            if (hasNumericOperators) {
                List<String> stringValues = new ArrayList<>();
                for (Object value : values) {
                    stringValues.add(value.toString());
                }
                return createNumericPredicate(cb, path, stringValues);
            }
        }

        if (values.size() == 1 && values.get(0) instanceof String strValue) {
            if (strValue.contains("!")) {
                String[] parts = strValue.split("!");
                List<Predicate> predicates = new ArrayList<>();
                for (String part : parts) {
                    String processedPart = removeAccents(part.trim().toLowerCase());
                    Expression<String> unaccentLowerExpr = getUnaccentLowerExpression(cb, path);
                    predicates.add(cb.equal(unaccentLowerExpr, processedPart));
                }
                return cb.or(predicates.toArray(new Predicate[0]));
            }
        }

        List<Predicate> predicates = new ArrayList<>();
        for (Object value : values) {
            if (String.class.isAssignableFrom(path.getJavaType())) {
                Expression<String> unaccentLowerExpr = getUnaccentLowerExpression(cb, path);
                predicates.add(cb.like(unaccentLowerExpr, padraoContem((String) value), ESCAPE_LIKE));
            } else {
                predicates.add(cb.equal(path, value));
            }
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }

    private static boolean isDateType(Path<?> path) {
        Class<?> type = path.getJavaType();
        return type.equals(LocalDate.class) || type.equals(LocalDateTime.class)
                || type.equals(Instant.class) || type.equals(Date.class);
    }

    private static Predicate createDatePredicate(CriteriaBuilder cb, Path<?> path, List<String> values) {
        List<Predicate> predicates = new ArrayList<>();
        Object minValue = null;
        Object maxValue = null;
        boolean minInclusive = true;
        boolean maxInclusive = true;

        for (String value : values) {
            if (value == null)
                continue;

            String[] operadorEValor = separarOperador(value);
            String operator = operadorEValor[0];
            String dateValue = operadorEValor[1];

            Object parsedDate = parseDate(path.getJavaType(), dateValue);

            switch (operator) {
                case "gte", "gt" :
                    boolean inclusiveMinimum = operator.equals("gte");
                    int minimumComparison = minValue == null ? 1 : compareDates(parsedDate, minValue);
                    if (minValue == null || minimumComparison > 0
                            || (minimumComparison == 0 && !inclusiveMinimum && minInclusive)) {
                        minValue = parsedDate;
                        minInclusive = inclusiveMinimum;
                    }
                    break;
                case "lte", "lt" :
                    boolean inclusiveMaximum = operator.equals("lte");
                    int maximumComparison = maxValue == null ? -1 : compareDates(parsedDate, maxValue);
                    if (maxValue == null || maximumComparison < 0
                            || (maximumComparison == 0 && !inclusiveMaximum && maxInclusive)) {
                        maxValue = parsedDate;
                        maxInclusive = inclusiveMaximum;
                    }
                    break;
                case "ne" :
                    predicates.add(cb.notEqual(path, parsedDate));
                    break;
                case "eq" :
                    predicates.add(cb.equal(path, parsedDate));
                    break;
                default :
                    throw operadorInvalido(operator);
            }
        }

        if (minValue != null) {
            predicates.add(compararData(cb, path, minValue, true, minInclusive));
        }
        if (maxValue != null) {
            predicates.add(compararData(cb, path, maxValue, false, maxInclusive));
        }

        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    }

    private static Predicate compararData(CriteriaBuilder cb, Path<?> path, Object value,
                                          boolean limiteInferior, boolean inclusivo) {
        Class<?> type = path.getJavaType();
        if (type.equals(LocalDate.class)) {
            return compararPorLimite(cb, path.as(LocalDate.class), (LocalDate) value, limiteInferior, inclusivo);
        }
        if (type.equals(LocalDateTime.class)) {
            return compararPorLimite(cb, path.as(LocalDateTime.class), (LocalDateTime) value,
                    limiteInferior, inclusivo);
        }
        if (type.equals(Instant.class)) {
            return compararPorLimite(cb, path.as(Instant.class), (Instant) value, limiteInferior, inclusivo);
        }
        if (type.equals(Date.class)) {
            return compararPorLimite(cb, path.as(Date.class), (Date) value, limiteInferior, inclusivo);
        }
        throw new UnsupportedOperationException("Tipo de data não suportado: " + type);
    }

    private static <T extends Comparable<? super T>> Predicate compararPorLimite(
            CriteriaBuilder cb, Expression<T> expression, T value, boolean limiteInferior, boolean inclusivo) {
        if (limiteInferior) {
            return inclusivo
                    ? cb.greaterThanOrEqualTo(expression, value)
                    : cb.greaterThan(expression, value);
        }
        return inclusivo
                ? cb.lessThanOrEqualTo(expression, value)
                : cb.lessThan(expression, value);
    }

    /**
     * Separa "operador:valor". Só um operador conhecido conta como prefixo; assim valores com ':'
     * (como 2026-01-01T10:00) continuam sendo comparados por igualdade.
     */
    private static String[] separarOperador(String value) {
        int separador = value.indexOf(':');
        if (separador > 0) {
            String operador = value.substring(0, separador).toLowerCase(Locale.ROOT);
            if (OPERADORES.contains(operador)) {
                return new String[]{operador, value.substring(separador + 1)};
            }
        }
        return new String[]{"eq", value};
    }

    private static FiltroInvalidoException operadorInvalido(String operador) {
        return new FiltroInvalidoException(
                "Operador de filtro inválido: '" + operador + "'. Use eq, ne, gt, gte, lt ou lte.");
    }

    private static int compareDates(Object date1, Object date2) {
        if (date1 instanceof LocalDate && date2 instanceof LocalDate) {
            return ((LocalDate) date1).compareTo((LocalDate) date2);
        } else if (date1 instanceof LocalDateTime && date2 instanceof LocalDateTime) {
            return ((LocalDateTime) date1).compareTo((LocalDateTime) date2);
        } else if (date1 instanceof Instant && date2 instanceof Instant) {
            return ((Instant) date1).compareTo((Instant) date2);
        } else if (date1 instanceof Date && date2 instanceof Date) {
            return ((Date) date1).compareTo((Date) date2);
        }
        throw new IllegalArgumentException("Tipos de data incompatíveis para comparação");
    }

    private static Object parseDate(Class<?> type, String value) {
        try {
            if (type.equals(LocalDate.class)) {
                return LocalDate.parse(value);
            } else if (type.equals(LocalDateTime.class)) {
                if (value.contains("T")) {
                    return LocalDateTime.parse(value);
                } else {
                    return LocalDate.parse(value).atStartOfDay();
                }
            } else if (type.equals(Instant.class)) {
                return Instant.parse(value);
            } else if (type.equals(Date.class)) {
                return new SimpleDateFormat("yyyy-MM-dd").parse(value);
            }
        } catch (Exception e) {
            String formatoEsperado = type.equals(Instant.class) ? "AAAA-MM-DDTHH:MM:SSZ (UTC)" : "AAAA-MM-DD";
            throw new FiltroInvalidoException("Formato de data inválido: '" + value + "'. Use " + formatoEsperado + ".");
        }
        throw new UnsupportedOperationException("Tipo de data não suportado: " + type);
    }

    private static boolean isNumericType(Path<?> path) {
        Class<?> type = path.getJavaType();
        return type.equals(Integer.class) || type.equals(int.class) || type.equals(Long.class)
                || type.equals(long.class) || type.equals(Double.class) || type.equals(double.class)
                || type.equals(Float.class) || type.equals(float.class) || type.equals(Short.class)
                || type.equals(short.class) || type.equals(BigDecimal.class) || type.equals(BigInteger.class);
    }

    private static Predicate createNumericPredicate(CriteriaBuilder cb, Path<?> path, List<String> values) {
        List<Predicate> predicates = new ArrayList<>();
        Number minValue = null;
        Number maxValue = null;
        boolean minInclusive = true;
        boolean maxInclusive = true;

        for (String value : values) {
            if (value == null)
                continue;

            String[] operadorEValor = separarOperador(value);
            String operator = operadorEValor[0];
            String numericValue = operadorEValor[1];

            Number number = parseNumber(path.getJavaType(), numericValue);

            switch (operator) {
                case "gte", "gt" :
                    boolean inclusiveMinimum = operator.equals("gte");
                    int minimumComparison = minValue == null ? 1 : compareNumbers(number, minValue);
                    if (minValue == null || minimumComparison > 0
                            || (minimumComparison == 0 && !inclusiveMinimum && minInclusive)) {
                        minValue = number;
                        minInclusive = inclusiveMinimum;
                    }
                    break;
                case "lte", "lt" :
                    boolean inclusiveMaximum = operator.equals("lte");
                    int maximumComparison = maxValue == null ? -1 : compareNumbers(number, maxValue);
                    if (maxValue == null || maximumComparison < 0
                            || (maximumComparison == 0 && !inclusiveMaximum && maxInclusive)) {
                        maxValue = number;
                        maxInclusive = inclusiveMaximum;
                    }
                    break;
                case "ne" :
                    predicates.add(cb.notEqual(path, number));
                    break;
                case "eq" :
                    predicates.add(cb.equal(path, number));
                    break;
                default :
                    throw operadorInvalido(operator);
            }
        }

        if (minValue != null) {
            predicates.add(compararNumero(cb, path, minValue, true, minInclusive));
        }
        if (maxValue != null) {
            predicates.add(compararNumero(cb, path, maxValue, false, maxInclusive));
        }

        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    }

    private static Predicate compararNumero(CriteriaBuilder cb, Path<?> path, Number value,
                                            boolean limiteInferior, boolean inclusivo) {
        Class<?> type = path.getJavaType();
        if (type.equals(Integer.class) || type.equals(int.class)) {
            return compararPorLimite(cb, path.as(Integer.class), value.intValue(), limiteInferior, inclusivo);
        }
        if (type.equals(Long.class) || type.equals(long.class)) {
            return compararPorLimite(cb, path.as(Long.class), value.longValue(), limiteInferior, inclusivo);
        }
        if (type.equals(Double.class) || type.equals(double.class)) {
            return compararPorLimite(cb, path.as(Double.class), value.doubleValue(), limiteInferior, inclusivo);
        }
        if (type.equals(Float.class) || type.equals(float.class)) {
            return compararPorLimite(cb, path.as(Float.class), value.floatValue(), limiteInferior, inclusivo);
        }
        if (type.equals(Short.class) || type.equals(short.class)) {
            return compararPorLimite(cb, path.as(Short.class), value.shortValue(), limiteInferior, inclusivo);
        }
        if (type.equals(BigDecimal.class)) {
            return compararPorLimite(cb, path.as(BigDecimal.class), (BigDecimal) value, limiteInferior, inclusivo);
        }
        if (type.equals(BigInteger.class)) {
            return compararPorLimite(cb, path.as(BigInteger.class), (BigInteger) value, limiteInferior, inclusivo);
        }
        throw new UnsupportedOperationException("Tipo numérico não suportado: " + type);
    }

    private static Number parseNumber(Class<?> type, String value) {
        try {
            if (type.equals(Integer.class) || type.equals(int.class)) {
                return Integer.parseInt(value);
            } else if (type.equals(Long.class) || type.equals(long.class)) {
                return Long.parseLong(value);
            } else if (type.equals(Double.class) || type.equals(double.class)) {
                return Double.parseDouble(value);
            } else if (type.equals(Float.class) || type.equals(float.class)) {
                return Float.parseFloat(value);
            } else if (type.equals(Short.class) || type.equals(short.class)) {
                return Short.parseShort(value);
            } else if (type.equals(BigDecimal.class)) {
                return new BigDecimal(value);
            } else if (type.equals(BigInteger.class)) {
                return new BigInteger(value);
            }
        } catch (Exception e) {
            throw new FiltroInvalidoException("Formato numérico inválido: '" + value + "'.");
        }
        throw new UnsupportedOperationException("Tipo numérico não suportado: " + type);
    }

    private static int compareNumbers(Number num1, Number num2) {
        BigDecimal bd1 = new BigDecimal(num1.toString());
        BigDecimal bd2 = new BigDecimal(num2.toString());
        return bd1.compareTo(bd2);
    }


    private static Object convertValueToFieldType(Path<?> path, Object value) {
        if (value == null)
            return null;

        Class<?> fieldType = path.getJavaType();
        String stringValue = value.toString();

        try {
            if ((isDateType(path) || isNumericType(path)) && stringValue.contains(":")) {
                return stringValue;
            }

            if (fieldType.equals(String.class)) {
                return stringValue;
            } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
                return Long.parseLong(stringValue);
            } else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
                return Integer.parseInt(stringValue);
            } else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
                return Double.parseDouble(stringValue);
            } else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
                return Float.parseFloat(stringValue);
            } else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
                return Boolean.parseBoolean(stringValue);
            } else if (fieldType.equals(LocalDate.class)) {
                return LocalDate.parse(stringValue);
            } else if (fieldType.equals(LocalDateTime.class)) {
                return stringValue.contains("T")
                        ? LocalDateTime.parse(stringValue)
                        : LocalDate.parse(stringValue).atStartOfDay();
            } else if (fieldType.equals(Instant.class)) {
                return Instant.parse(stringValue);
            } else if (fieldType.equals(Date.class)) {
                return new SimpleDateFormat("yyyy-MM-dd").parse(stringValue);
            } else if (fieldType.isEnum()) {
                return Enum.valueOf((Class<Enum>) fieldType, stringValue);
            }
        } catch (Exception e) {
            throw new FiltroInvalidoException(
                    "Valor '" + stringValue + "' inválido para o tipo " + fieldType.getSimpleName() + ".");
        }

        return value;
    }
}

package br.ufla.citogenetica.filter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;

import jakarta.persistence.criteria.*;

public class CriteriaCreator {
    public static <T> Specification<T> byFilterMap(MultiValueMap<String, String> params, Class<T> clazz, String sortBy,
                                                   String sortDirection) {

        Map<String, List<Object>> filterMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            List<Object> values = new ArrayList<>(entry.getValue());
            filterMap.put(entry.getKey(), values);
        }

        filterMap.remove("page");
        filterMap.remove("size");
        filterMap.remove("sort");
        filterMap.remove("sortBy");
        filterMap.remove("sortDirection");

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

                            try {
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
                            } catch (Exception e) {
                                String errorMsg = String.format("Erro no campo '%s': %s", orKey, e.getMessage());
                            }
                        }

                        if (!currentOrPredicates.isEmpty()) {
                            orPredicates.add(cb.or(currentOrPredicates.toArray(new Predicate[0])));
                        }
                    } else {
                        Predicate predicate = createPredicateForEntry(root, cb, key, values);
                        finalPredicate = cb.and(finalPredicate, predicate);
                    }
                }
            }

            if (!orPredicates.isEmpty()) {
                finalPredicate = cb.and(finalPredicate, cb.or(orPredicates.toArray(new Predicate[0])));
            }

            if (sortBy != null && !sortBy.isEmpty()) {
                SortHelper.addSort(root, query, cb, sortBy, sortDirection);
            }

            return finalPredicate;
        };
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
        String processedValue = "%" + removeAccents(value.toString().toLowerCase()) + "%";

        Expression<String> unaccentLowerExpr = cb.function("unaccent", String.class,
                cb.function("lower", String.class, jsonExpr));

        return cb.like(unaccentLowerExpr, processedValue);
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
                String processedValue = "%" + removeAccents(((String) value).toLowerCase()) + "%";
                Expression<String> unaccentLowerExpr = getUnaccentLowerExpression(cb, path);
                predicates.add(cb.like(unaccentLowerExpr, processedValue));
            } else {
                predicates.add(cb.equal(path, value));
            }
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }

    private static boolean isDateType(Path<?> path) {
        Class<?> type = path.getJavaType();
        return type.equals(LocalDate.class) || type.equals(LocalDateTime.class) || type.equals(Date.class);
    }

    private static Predicate createDatePredicate(CriteriaBuilder cb, Path<?> path, List<String> values) {
        List<Predicate> predicates = new ArrayList<>();
        Object minValue = null;
        Object maxValue = null;

        for (String value : values) {
            if (value == null)
                continue;

            String[] parts = value.split(":");
            String operator = parts.length > 1 ? parts[0].toLowerCase() : "eq";
            String dateValue = parts.length > 1 ? parts[1] : value;

            try {
                Object parsedDate = parseDate(path.getJavaType(), dateValue);

                switch (operator) {
                    case "gte" :
                        if (minValue == null || compareDates(parsedDate, minValue) > 0) {
                            minValue = parsedDate;
                        }
                        break;
                    case "gt" :
                        if (minValue == null || compareDates(parsedDate, minValue) >= 0) {
                            minValue = parsedDate;
                        }
                        break;
                    case "lte" :
                        if (maxValue == null || compareDates(parsedDate, maxValue) < 0) {
                            maxValue = parsedDate;
                        }
                        break;
                    case "lt" :
                        if (maxValue == null || compareDates(parsedDate, maxValue) <= 0) {
                            maxValue = parsedDate;
                        }
                        break;
                    case "ne" :
                        predicates.add(cb.notEqual(path, parsedDate));
                        break;
                    case "eq" :
                    default :
                        predicates.add(cb.equal(path, parsedDate));
                        break;
                }
            } catch (Exception e) {
                String processedValue = "%" + removeAccents(value.toLowerCase()) + "%";
                Expression<String> unaccentLowerExpr = getUnaccentLowerExpression(cb, path);
                predicates.add(cb.like(unaccentLowerExpr, processedValue));
            }
        }

        if (minValue != null || maxValue != null) {
            Class<?> type = path.getJavaType();
            if (minValue != null && maxValue != null) {
                if (type.equals(LocalDate.class)) {
                    predicates.add(cb.between(path.as(LocalDate.class), (LocalDate) minValue, (LocalDate) maxValue));
                } else if (type.equals(LocalDateTime.class)) {
                    predicates.add(cb.between(path.as(LocalDateTime.class), (LocalDateTime) minValue,
                            (LocalDateTime) maxValue));
                } else if (type.equals(Date.class)) {
                    predicates.add(cb.between(path.as(Date.class), (Date) minValue, (Date) maxValue));
                }
            } else if (minValue != null) {
                if (type.equals(LocalDate.class)) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(LocalDate.class), (LocalDate) minValue));
                } else if (type.equals(LocalDateTime.class)) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(LocalDateTime.class), (LocalDateTime) minValue));
                } else if (type.equals(Date.class)) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(Date.class), (Date) minValue));
                }
            } else {
                if (type.equals(LocalDate.class)) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(LocalDate.class), (LocalDate) maxValue));
                } else if (type.equals(LocalDateTime.class)) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(LocalDateTime.class), (LocalDateTime) maxValue));
                } else if (type.equals(Date.class)) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(Date.class), (Date) maxValue));
                }
            }
        }

        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
    }

    private static int compareDates(Object date1, Object date2) {
        if (date1 instanceof LocalDate && date2 instanceof LocalDate) {
            return ((LocalDate) date1).compareTo((LocalDate) date2);
        } else if (date1 instanceof LocalDateTime && date2 instanceof LocalDateTime) {
            return ((LocalDateTime) date1).compareTo((LocalDateTime) date2);
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
            } else if (type.equals(Date.class)) {
                return new SimpleDateFormat("yyyy-MM-dd").parse(value);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de data inválido: " + value);
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

        for (String value : values) {
            if (value == null)
                continue;

            String[] parts = value.split(":");
            String operator = parts.length > 1 ? parts[0].toLowerCase() : "eq";
            String numericValue = parts.length > 1 ? parts[1] : value;

            try {
                Number number = parseNumber(path.getJavaType(), numericValue);

                switch (operator) {
                    case "gte" :
                        if (minValue == null || compareNumbers(number, minValue) > 0) {
                            minValue = number;
                        }
                        break;
                    case "gt" :
                        if (minValue == null || compareNumbers(number, minValue) >= 0) {
                            minValue = number;
                        }
                        break;
                    case "lte" :
                        if (maxValue == null || compareNumbers(number, maxValue) < 0) {
                            maxValue = number;
                        }
                        break;
                    case "lt" :
                        if (maxValue == null || compareNumbers(number, maxValue) <= 0) {
                            maxValue = number;
                        }
                        break;
                    case "ne" :
                        predicates.add(cb.notEqual(path, number));
                        break;
                    case "eq" :
                    default :
                        predicates.add(cb.equal(path, number));
                        break;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Valor numérico inválido: " + value + ". Detalhe: " + e.getMessage());
            }
        }

        if (minValue != null || maxValue != null) {
            Class<?> expr = path.getJavaType();

            boolean bInt = expr.equals(Integer.class) || expr.equals(int.class);
            boolean bLong = expr.equals(Long.class) || expr.equals(long.class);
            boolean bDouble = expr.equals(Double.class) || expr.equals(double.class);
            boolean bFloat = expr.equals(Float.class) || expr.equals(float.class);
            boolean bShort = expr.equals(Short.class) || expr.equals(short.class);
            boolean bBigDecimal = expr.equals(BigDecimal.class);
            boolean bBigInteger = expr.equals(BigInteger.class);

            if (minValue != null && maxValue != null) {
                if (bInt) {
                    predicates.add(cb.between(path.as(Integer.class), (Integer) minValue, (Integer) maxValue));
                } else if (bLong) {
                    predicates.add(cb.between(path.as(Long.class), (Long) minValue, (Long) maxValue));
                } else if (bDouble) {
                    predicates.add(cb.between(path.as(Double.class), (Double) minValue, (Double) maxValue));
                } else if (bFloat) {
                    predicates.add(cb.between(path.as(Float.class), (Float) minValue, (Float) maxValue));
                } else if (bShort) {
                    predicates.add(cb.between(path.as(Short.class), (Short) minValue, (Short) maxValue));
                } else if (bBigDecimal) {
                    predicates.add(cb.between(path.as(BigDecimal.class), (BigDecimal) minValue, (BigDecimal) maxValue));
                } else if (bBigInteger) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(BigInteger.class), (BigInteger) minValue));
                }
            } else if (minValue != null) {
                if (bInt) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(Integer.class), (Integer) minValue));
                } else if (bLong) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(Long.class), (Long) minValue));
                } else if (bDouble) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(Double.class), (Double) minValue));
                } else if (bFloat) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(Float.class), (Float) minValue));
                } else if (bShort) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(Short.class), (Short) minValue));
                } else if (bBigDecimal) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(BigDecimal.class), (BigDecimal) minValue));
                } else if (bBigInteger) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(BigInteger.class), (BigInteger) minValue));
                }
            } else {
                if (bInt) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(Integer.class), (Integer) maxValue));
                } else if (bLong) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(Long.class), (Long) maxValue));
                } else if (bDouble) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(Double.class), (Double) maxValue));
                } else if (bFloat) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(Float.class), (Float) maxValue));
                } else if (bShort) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(Short.class), (Short) maxValue));
                } else if (bBigDecimal) {
                    predicates.add(cb.lessThanOrEqualTo(path.as(BigDecimal.class), (BigDecimal) maxValue));
                } else if (bBigInteger) {
                    predicates.add(cb.greaterThanOrEqualTo(path.as(BigInteger.class), (BigInteger) minValue));
                }
            }
        }

        return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
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
            throw new IllegalArgumentException("Formato numérico inválido: " + value);
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
            } else if (fieldType.equals(Date.class)) {
                return new SimpleDateFormat("yyyy-MM-dd").parse(stringValue);
            } else if (fieldType.isEnum()) {
                return Enum.valueOf((Class<Enum>) fieldType, stringValue);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Valor '" + stringValue + "' inválido para tipo " + fieldType.getSimpleName());
        }

        return value;
    }
}
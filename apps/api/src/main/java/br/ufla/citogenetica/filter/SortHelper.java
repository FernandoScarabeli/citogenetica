package br.ufla.citogenetica.filter;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;

public class SortHelper {

    public static <T> void addSort(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb, String sortBy, String sortDirection) {
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            Order order;
            if ("desc".equalsIgnoreCase(sortDirection)) {
                order = cb.desc(root.get(sortBy));
            } else {
                order = cb.asc(root.get(sortBy));
            }
            query.orderBy(order);
        }
    }
}
package com.ecommerce.common.web;

import com.ecommerce.common.exception.BusinessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Lets a caller sort only by fields the API chooses to expose. A client-supplied sort property otherwise reaches
 * the persistence layer verbatim: an unknown one is a 500 (or worse, a way to probe the schema), and sorting a
 * large table by an unindexed column is an easy way to hurt the database.
 */
public final class SortGuard {

    private SortGuard() {
    }

    /** Returns the pageable unchanged if every sort property is allowed; otherwise a 400. */
    public static Pageable requireSortableBy(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException("Cannot sort by '" + order.getProperty() + "'. Allowed: " + allowed, "INVALID_SORT_FIELD");
            }
        }
        return pageable;
    }
}

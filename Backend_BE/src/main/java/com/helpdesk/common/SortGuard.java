package com.helpdesk.common;

import com.helpdesk.common.exception.ApiException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Only whitelisted properties may be used in ?sort=, so a bad value is a clean
 * 400 instead of a database error.
 */
public final class SortGuard {

    private SortGuard() {
    }

    public static Pageable check(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw ApiException.badRequest("Cannot sort by '" + order.getProperty() + "'. Allowed: " + allowed);
            }
        }
        return pageable;
    }
}

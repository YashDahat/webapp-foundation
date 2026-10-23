package com.webappfoundation.model;

/**
 * Where a {@link Product} is offered. {@code GLOBAL} (default) = every branch; {@code SELECTED} = only
 * the branches listed in {@code Product.availableBranchIds}. Filters the public catalog + recommendations
 * by the selected branch and is validated at checkout.
 */
public enum BranchScope {
    GLOBAL,
    SELECTED
}

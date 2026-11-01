package com.webappfoundation.model;

/**
 * The reward an {@code Offer} grants. "Cheapest reward first": non-cash rungs ({@code FREE_SHIP},
 * {@code GIFT}, {@code COINS}) are tried before the cash rungs ({@code PCT}, {@code FLAT}). Only
 * {@code PCT}/{@code FLAT} draw from the monthly discount budget.
 */
public enum OfferType {
    PCT,
    FLAT,
    FREE_SHIP,
    GIFT,
    COINS
}

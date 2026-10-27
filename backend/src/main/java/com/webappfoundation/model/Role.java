package com.webappfoundation.model;

public enum Role {
    // SUPER_ADMIN is a single-holder role with all ADMIN rights plus the ability to delete users.
    SUPER_ADMIN,
    ADMIN,
    // STAFF is an operational role below ADMIN: it may view the order board and change ticket status
    // (advance stage, block/resume) only — no cancel/refund/create and no other admin area. See SecurityConfig.
    STAFF,
    USER
}

package com.webappfoundation.model;

public enum Role {
    // SUPER_ADMIN is a single-holder role with all ADMIN rights plus the ability to delete users.
    SUPER_ADMIN,
    ADMIN,
    USER
}

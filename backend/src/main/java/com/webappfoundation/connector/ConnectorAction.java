package com.webappfoundation.connector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A named action a connector can be bound to at a stage, declared so the admin UI can offer it. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorAction {
    private String code;
    private String label;
}

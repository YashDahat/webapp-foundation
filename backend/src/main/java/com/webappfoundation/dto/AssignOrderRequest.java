package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for assigning a board order to a STAFF user; {@code userId == null} unassigns. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignOrderRequest {
    private Integer userId;
}

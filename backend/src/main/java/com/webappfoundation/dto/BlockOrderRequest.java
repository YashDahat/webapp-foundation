package com.webappfoundation.dto;

import com.webappfoundation.model.BlockReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for blocking an order on the board: a reason + an optional free-text note. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockOrderRequest {
    private BlockReason reason;
    private String note;
}

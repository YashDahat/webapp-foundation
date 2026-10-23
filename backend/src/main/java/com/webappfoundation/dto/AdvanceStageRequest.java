package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for an admin manual stage transition. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvanceStageRequest {
    private String stageCode;
}

package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A support case plus its full ordered message thread. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportThreadDto {
    private SupportCaseDto supportCase;
    private List<SupportMessageDto> messages;
}

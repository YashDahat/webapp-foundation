package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * The board read model for one {@code (workflowCode, sprint, branch?)} scope. Columns are the workflow stages
 * (ordinal order) plus a synthetic <b>Blocked</b> column and the terminal <b>Cancelled</b> column. A null
 * applied {@code branchId} means <b>all branches</b>; {@code branches} always carries the per-branch split for
 * the whole sprint so a multi-outlet owner sees every outlet at once.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBoardDto {
    private String workflowCode;
    private String workflowLabel;
    private String staffRole;
    /** The selected (or active) sprint; null when the workflow has no sprint yet. */
    private SprintDto sprint;
    /** The applied branch filter; null = all branches. */
    private Long branchId;
    private List<Column> columns;
    private FlagCounts flags;
    private List<BranchCount> branches;
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column {
        /** STAGE | BLOCKED | CANCELLED — drives the UI's valid drop-target rules. */
        private String kind;
        private String stageCode;
        private String label;
        private Integer ordinal;
        private Integer targetMinutes;
        private int count;
        private int stuckCount;
        private List<OrderCardDto> cards;
    }

    /** Filter-bar counts (flag toggles with a count badge), scoped to the current board view. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlagCounts {
        private int blocked;
        private int stuck;
        private int unpaid;
        private int unassigned;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchCount {
        private Long branchId;
        private String branchName;
        private int count;
    }
}

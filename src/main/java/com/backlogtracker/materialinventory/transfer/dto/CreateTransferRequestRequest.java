package com.backlogtracker.materialinventory.transfer.dto;

import java.util.List;

/** One request can now ask for several yarn types from the same target in one go — each
 *  {@link LineInput} becomes its own independently-tracked line. */
public record CreateTransferRequestRequest(String targetUserId, List<LineInput> lines) {

    public record LineInput(String yarnTypeId, double quantity) {
    }
}

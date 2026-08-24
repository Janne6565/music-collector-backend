package com.musiccollector.model.action;

import com.musiccollector.model.core.SyncCopyDto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SyncPushRequest(
        /** Batched rather than unbounded so one client cannot post its whole history at once. */
        @NotNull @Size(max = 500, message = "Push at most 500 copies per request")
        List<SyncCopyDto> copies) {}

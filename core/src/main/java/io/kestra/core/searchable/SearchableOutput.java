package io.kestra.core.searchable;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;
import jakarta.validation.constraints.NotNull;

public record SearchableOutput(@NotNull String tenantId, @NotNull String executionId, @NotNull String outputId, String value) implements HasUID {
    @Override
    public String uid() {
        return IdUtils.fromParts(tenantId, executionId, outputId);
    }
}

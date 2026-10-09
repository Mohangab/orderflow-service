package com.mohan.orderflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @Schema(example = "Changed my mind")
        @Size(max = 255) String reason
) {
}

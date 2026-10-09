package com.mohan.orderflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @Schema(example = "customer-42")
        @NotBlank @Size(max = 64) String customerId,

        @NotEmpty @Size(max = 50) List<@Valid OrderItemRequest> items
) {
}

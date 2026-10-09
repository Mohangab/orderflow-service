package com.mohan.orderflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderItemRequest(
        @Schema(example = "SKU-KEYBOARD-01")
        @NotBlank @Size(max = 64) String sku,

        @Schema(example = "2")
        @Min(1) @Max(1000) int quantity,

        @Schema(example = "49.99")
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice
) {
}

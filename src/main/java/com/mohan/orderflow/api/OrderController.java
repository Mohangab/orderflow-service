package com.mohan.orderflow.api;

import com.mohan.orderflow.api.dto.CancelOrderRequest;
import com.mohan.orderflow.api.dto.CreateOrderRequest;
import com.mohan.orderflow.api.dto.OrderResponse;
import com.mohan.orderflow.api.dto.PageResponse;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Create, query and cancel orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place a new order",
            description = "Persists the order as PENDING; processing happens asynchronously via Kafka.")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Optional filters by customer and status; paginated and sortable.")
    public PageResponse<OrderResponse> list(
            @Parameter(description = "Filter by customer id") @RequestParam(required = false) String customerId,
            @Parameter(description = "Filter by status") @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.listOrders(customerId, status, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order", description = "Allowed while the order is PENDING or CONFIRMED.")
    public OrderResponse cancel(@PathVariable UUID id,
                                @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancelOrder(id, request == null ? null : request.reason());
    }
}

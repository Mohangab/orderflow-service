package com.mohan.orderflow.api;

import com.mohan.orderflow.api.dto.CreateOrderRequest;
import com.mohan.orderflow.api.dto.OrderItemResponse;
import com.mohan.orderflow.api.dto.OrderResponse;
import com.mohan.orderflow.api.dto.PageResponse;
import com.mohan.orderflow.domain.IllegalOrderStateException;
import com.mohan.orderflow.domain.OrderStatus;
import com.mohan.orderflow.service.OrderNotFoundException;
import com.mohan.orderflow.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void createReturns201WithLocation() throws Exception {
        OrderResponse created = sample(OrderStatus.PENDING);
        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"customer-1","items":[{"sku":"SKU-A","quantity":2,"unitPrice":10.00}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/orders/" + created.id())))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-A"));
    }

    @Test
    void createRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"","items":[{"sku":"SKU-A","quantity":0,"unitPrice":-1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.customerId").exists())
                .andExpect(jsonPath("$.errors['items[0].quantity']").exists())
                .andExpect(jsonPath("$.errors['items[0].unitPrice']").exists());
        verifyNoInteractions(orderService);
    }

    @Test
    void createRejectsEmptyItems() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"c\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenThrow(new OrderNotFoundException(id));

        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void listPassesFiltersAndReturnsPage() throws Exception {
        when(orderService.listOrders(eq("customer-1"), eq(OrderStatus.CONFIRMED), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sample(OrderStatus.CONFIRMED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/orders").param("customerId", "customer-1").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    void cancelInFinalStateReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.cancelOrder(eq(id), isNull()))
                .thenThrow(new IllegalOrderStateException(id, OrderStatus.REJECTED, OrderStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid order state transition"));
    }

    private static OrderResponse sample(OrderStatus status) {
        return new OrderResponse(UUID.randomUUID(), "customer-1", status, null, new BigDecimal("20.00"),
                List.of(new OrderItemResponse("SKU-A", 2, new BigDecimal("10.00"), new BigDecimal("20.00"))),
                Instant.now(), Instant.now());
    }
}

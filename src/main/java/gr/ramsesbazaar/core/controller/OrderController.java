package gr.ramsesbazaar.core.controller;

import gr.ramsesbazaar.core.dto.OrderDto;
import gr.ramsesbazaar.core.dto.OrderReceipt;
import gr.ramsesbazaar.core.dto.OrderRequest;
import gr.ramsesbazaar.core.dto.UpdateOrderStatusRequest;
import gr.ramsesbazaar.core.security.RequireAdmin;
import gr.ramsesbazaar.core.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer orders")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Place an order", description = "Totals are computed from catalogue prices.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order received"),
            @ApiResponse(responseCode = "409", description = "A product is no longer available"),
            @ApiResponse(responseCode = "422", description = "Missing or invalid fields")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderReceipt place(@RequestBody OrderRequest request) {
        if (request.website() != null && !request.website().isEmpty()) {
            // A bot filled in the hidden field: look successful, store nothing.
            return new OrderReceipt(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return orderService.place(request);
    }

    @Operation(summary = "List recent orders", description = "Newest first, at most 200.")
    @RequireAdmin
    @GetMapping
    public List<OrderDto> recent() {
        return orderService.recent();
    }

    @Operation(summary = "Change an order's status", description = "NEW, CONFIRMED, SHIPPED or CANCELLED.")
    @RequireAdmin
    @PatchMapping("/{id}")
    public OrderDto updateStatus(@PathVariable UUID id, @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}

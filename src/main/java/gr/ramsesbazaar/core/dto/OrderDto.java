package gr.ramsesbazaar.core.dto;

import gr.ramsesbazaar.core.entity.OrderItem;
import gr.ramsesbazaar.core.entity.ShopOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "Order", description = "An order as the admin panel lists it")
public record OrderDto(
        UUID id,
        String status,
        String name,
        String phone,
        String email,
        String address,
        String notes,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal shipping,
        BigDecimal total,
        Instant createdAt
) {

    public record Item(String productId, String name, BigDecimal price, int qty) {

        static Item from(OrderItem i) {
            return new Item(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity());
        }
    }

    public static OrderDto from(ShopOrder o) {
        return new OrderDto(o.getId(), o.getStatus().name(), o.getCustomerName(), o.getCustomerPhone(),
                o.getCustomerEmail(), o.getCustomerAddress(), o.getNotes(),
                o.getItems().stream().map(Item::from).toList(),
                o.getSubtotal(), o.getShipping(), o.getTotal(), o.getCreatedAt());
    }
}

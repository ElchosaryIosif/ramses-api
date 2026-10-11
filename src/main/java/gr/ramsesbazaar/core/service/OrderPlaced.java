package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.entity.ShopOrder;

import java.math.BigDecimal;
import java.util.List;

/** Published once an order is stored; a plain copy so listeners never touch the entity. */
public record OrderPlaced(String name, String phone, String email, String address, String notes,
                          List<Line> items, BigDecimal shipping, BigDecimal total) {

    public record Line(String name, int qty, BigDecimal lineTotal) {
    }

    static OrderPlaced from(ShopOrder o) {
        List<Line> lines = o.getItems().stream()
                .map(i -> new Line(i.getProductName(), i.getQuantity(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderPlaced(o.getCustomerName(), o.getCustomerPhone(), o.getCustomerEmail(),
                o.getCustomerAddress(), o.getNotes(), lines, o.getShipping(), o.getTotal());
    }
}

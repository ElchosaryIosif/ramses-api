package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.ApiException;
import gr.ramsesbazaar.core.dto.OrderDto;
import gr.ramsesbazaar.core.dto.OrderReceipt;
import gr.ramsesbazaar.core.dto.OrderRequest;
import gr.ramsesbazaar.core.entity.OrderItem;
import gr.ramsesbazaar.core.entity.OrderStatus;
import gr.ramsesbazaar.core.entity.Product;
import gr.ramsesbazaar.core.entity.ShopOrder;
import gr.ramsesbazaar.core.repository.ProductRepository;
import gr.ramsesbazaar.core.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Must match src/services/shipping.ts in the shop front. */
    public static final BigDecimal SHIPPING_COST = new BigDecimal("3.50");
    public static final BigDecimal FREE_SHIPPING_AT = new BigDecimal("50.00");
    private static final int MAX_LINES = 50;
    private static final int MAX_QTY = 99;
    private static final int LIST_LIMIT = 200;

    private final ShopOrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;

    /** Prices come from the catalogue, never from the request. */
    @Transactional
    public OrderReceipt place(OrderRequest req) {
        if (req == null) throw bad("invalid_order");
        String name = required(req.name(), 160);
        String phone = required(req.phone(), 40);
        String email = required(req.email(), 200);
        String address = required(req.address(), 500);
        if (!email.contains("@")) throw bad("invalid_email");
        List<OrderRequest.Line> lines = req.items();
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) throw bad("invalid_order");

        Map<String, Product> catalogue = productRepository.findAllById(
                        lines.stream().map(OrderRequest.Line::id).filter(id -> id != null).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        ShopOrder order = new ShopOrder();
        order.setCustomerName(name);
        order.setCustomerPhone(phone);
        order.setCustomerEmail(email);
        order.setCustomerAddress(address);
        order.setNotes(optional(req.notes(), 2000));

        BigDecimal subtotal = BigDecimal.ZERO;
        int position = 0;
        for (OrderRequest.Line line : lines) {
            Product p = catalogue.get(line.id());
            if (p == null || !p.isInStock()) throw new ApiException(HttpStatus.CONFLICT, "unavailable_product");
            if (line.qty() == null || line.qty() < 1 || line.qty() > MAX_QTY) throw bad("invalid_order");
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setPosition(position++);
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setUnitPrice(p.getPrice());
            item.setQuantity(line.qty());
            order.getItems().add(item);
            subtotal = subtotal.add(p.getPrice().multiply(BigDecimal.valueOf(line.qty())));
        }
        BigDecimal shipping = subtotal.compareTo(FREE_SHIPPING_AT) >= 0 ? BigDecimal.ZERO : SHIPPING_COST;
        order.setSubtotal(subtotal);
        order.setShipping(shipping);
        order.setTotal(subtotal.add(shipping));

        ShopOrder saved = orderRepository.save(order);
        events.publishEvent(OrderPlaced.from(saved));
        return new OrderReceipt(saved.getId(), saved.getSubtotal(), saved.getShipping(), saved.getTotal());
    }

    @Transactional(readOnly = true)
    public List<OrderDto> recent() {
        return orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, LIST_LIMIT))
                .stream().map(OrderDto::from).toList();
    }

    @Transactional
    public OrderDto updateStatus(UUID id, String status) {
        ShopOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found"));
        try {
            order.setStatus(OrderStatus.valueOf(status));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw bad("invalid_status");
        }
        return OrderDto.from(order);
    }

    private static String required(String v, int max) {
        String t = optional(v, max);
        if (t.isEmpty()) throw bad("missing_fields");
        return t;
    }

    private static String optional(String v, int max) {
        if (v == null) return "";
        String t = v.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static ApiException bad(String code) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code);
    }
}

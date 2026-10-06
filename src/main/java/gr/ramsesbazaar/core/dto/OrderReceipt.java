package gr.ramsesbazaar.core.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderReceipt(UUID id, BigDecimal subtotal, BigDecimal shipping, BigDecimal total) {
}

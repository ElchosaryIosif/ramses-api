package gr.ramsesbazaar.core.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNotifierTest {

    private static OrderPlaced order(String notes, String shipping, OrderPlaced.Line... lines) {
        return new OrderPlaced("Μαρία Π.", "6901234567", "m@x.gr", "Αριστομένους 1, Καλαμάτα", notes,
                List.of(lines), new BigDecimal(shipping), new BigDecimal("43.5"));
    }

    @Test
    void emailListsCustomerItemsAndTotal() {
        OrderPlaced o = order("Δώρο, χωρίς τιμή", "3.5",
                new OrderPlaced.Line("Ασημένιο δαχτυλίδι", 2, new BigDecimal("40")));

        assertThat(OrderNotifier.subject(o)).isEqualTo("Νέα παραγγελία – Μαρία Π. – €43.50");
        assertThat(OrderNotifier.body(o))
                .contains("Όνομα: Μαρία Π.", "Τηλέφωνο: 6901234567", "Email: m@x.gr",
                        "Διεύθυνση: Αριστομένους 1, Καλαμάτα")
                .contains("• Ασημένιο δαχτυλίδι ×2 — €40.00")
                .contains("Μεταφορικά: €3.50", "Σύνολο: €43.50")
                .contains("Σημειώσεις: Δώρο, χωρίς τιμή")
                .contains(OrderNotifier.ADMIN_URL);
    }

    @Test
    void freeShippingAndNoNotes() {
        String body = OrderNotifier.body(order("", "0",
                new OrderPlaced.Line("Λιβάνι", 1, new BigDecimal("60"))));

        assertThat(body).contains("Δωρεάν μεταφορικά").doesNotContain("Σημειώσεις");
    }
}

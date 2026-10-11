package gr.ramsesbazaar.core.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNotifierTest {

    private static OrderPlaced order(String notes, String shipping, OrderPlaced.Line... lines) {
        return new OrderPlaced("Μαρία Π.", "6901234567", "m@x.gr", "Αριστομένους 1, Καλαμάτα", notes,
                List.of(lines), new BigDecimal(shipping), new BigDecimal("43.50"));
    }

    @Test
    void messageListsCustomerItemsAndTotal() {
        String text = OrderNotifier.message(order("Δώρο, χωρίς τιμή", "3.5",
                new OrderPlaced.Line("Ασημένιο δαχτυλίδι", 2, new BigDecimal("40"))));

        assertThat(text)
                .startsWith("🛍️ *Νέα παραγγελία* – €43.50\n")
                .contains("👤 Μαρία Π.", "📞 6901234567", "✉️ m@x.gr", "📍 Αριστομένους 1, Καλαμάτα")
                .contains("• Ασημένιο δαχτυλίδι ×2 — €40.00")
                .contains("Μεταφορικά: €3.50")
                .contains("📝 Δώρο, χωρίς τιμή")
                .endsWith(OrderNotifier.ADMIN_URL);
    }

    @Test
    void freeShippingAndNoNotes() {
        String text = OrderNotifier.message(order("", "0",
                new OrderPlaced.Line("Λιβάνι", 1, new BigDecimal("60"))));

        assertThat(text).contains("Δωρεάν μεταφορικά").doesNotContain("📝");
    }

    @Test
    void longOrdersAreCutButKeepTheAdminLink() {
        OrderPlaced.Line[] many = new OrderPlaced.Line[50];
        for (int i = 0; i < many.length; i++) {
            many[i] = new OrderPlaced.Line("Προϊόν με αρκετά μεγάλο όνομα νούμερο " + i, 1, BigDecimal.ONE);
        }
        String text = OrderNotifier.message(order("", "0", many));

        assertThat(text.length()).isLessThanOrEqualTo(1500);
        assertThat(text).contains("…").endsWith(OrderNotifier.ADMIN_URL);
    }
}

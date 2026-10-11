package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Emails the shop for every new order through Brevo's transactional email API
 * (https://developers.brevo.com/reference/sendtransacemail). It goes over HTTPS rather
 * than SMTP, which Railway blocks on some plans. The order is already stored when this
 * runs, so a failed email never loses an order: it is still in the admin panel.
 */
@Slf4j
@Component
public class OrderNotifier {

    static final String ADMIN_URL = "https://ramsesbazaar.com/#admin";
    private static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");
    private static final int ATTEMPTS = 2;

    private final AppProperties.Mail config;
    private final SettingsService settings;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "order-notifier");
        t.setDaemon(true);
        return t;
    });

    public OrderNotifier(AppProperties props, SettingsService settings) {
        this.config = props.mail();
        this.settings = settings;
        if (!config.enabled()) {
            log.info("BREVO_API_KEY / ORDER_EMAIL_FROM not set: no email for new orders");
        }
    }

    /** Runs after the order's transaction commits; the customer does not wait for the email. */
    @TransactionalEventListener
    public void onOrderPlaced(OrderPlaced order) {
        if (!config.enabled()) return;
        sender.execute(() -> send(order));
    }

    static String subject(OrderPlaced o) {
        return "Νέα παραγγελία – " + o.name() + " – " + euro(o.total());
    }

    static String body(OrderPlaced o) {
        StringBuilder b = new StringBuilder()
                .append("Νέα παραγγελία από το ramsesbazaar.com\n\n")
                .append("Όνομα: ").append(o.name()).append('\n')
                .append("Τηλέφωνο: ").append(o.phone()).append('\n')
                .append("Email: ").append(o.email()).append('\n')
                .append("Διεύθυνση: ").append(o.address()).append("\n\n");
        for (OrderPlaced.Line l : o.items()) {
            b.append("• ").append(l.name()).append(" ×").append(l.qty())
                    .append(" — ").append(euro(l.lineTotal())).append('\n');
        }
        b.append(o.shipping().signum() > 0 ? "Μεταφορικά: " + euro(o.shipping()) : "Δωρεάν μεταφορικά").append('\n')
                .append("Σύνολο: ").append(euro(o.total())).append('\n');
        if (o.notes() != null && !o.notes().isBlank()) {
            b.append("\nΣημειώσεις: ").append(o.notes()).append('\n');
        }
        return b.append("\nΑπάντηση σε αυτό το email πηγαίνει στον πελάτη.\n")
                .append("Διαχείριση παραγγελιών: ").append(ADMIN_URL).append('\n')
                .toString();
    }

    private void send(OrderPlaced order) {
        String to = recipient();
        if (to.isEmpty()) {
            log.warn("New order not emailed: set ORDER_EMAIL_TO or the shop email in the admin panel");
            return;
        }
        String payload = json.writeValueAsString(Map.of(
                "sender", Map.of("name", "Ramses Bazaar", "email", config.from().trim()),
                "to", List.of(Map.of("email", to)),
                "replyTo", Map.of("email", order.email(), "name", order.name()),
                "subject", subject(order),
                "textContent", body(order)));
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(30))
                .header("api-key", config.brevoApiKey().trim())
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 == 2) return;
                log.warn("Order email failed (attempt {}): HTTP {} {}", attempt, res.statusCode(), res.body());
                // A rejected key or sender will not fix itself on a retry.
                if (res.statusCode() / 100 == 4) return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Order email failed (attempt {}): {}", attempt, e.toString());
            }
            if (attempt < ATTEMPTS) {
                try {
                    Thread.sleep(Duration.ofSeconds(20));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private String recipient() {
        if (config.to() != null && !config.to().isBlank()) return config.to().trim();
        try {
            String shop = settings.current().getEmail();
            return shop == null ? "" : shop.trim();
        } catch (RuntimeException e) {
            log.warn("Could not read the shop email: {}", e.toString());
            return "";
        }
    }

    private static String euro(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

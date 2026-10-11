package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends the shop a WhatsApp message for every new order through CallMeBot
 * (https://www.callmebot.com/blog/free-api-whatsapp-messages/). The order is already
 * stored when this runs, so a failed message never loses an order: it is still in the
 * admin panel.
 */
@Slf4j
@Component
public class OrderNotifier {

    static final String ADMIN_URL = "https://ramsesbazaar.com/#admin";
    private static final String ENDPOINT = "https://api.callmebot.com/whatsapp.php";
    private static final int MAX_TEXT = 1500;
    private static final int ATTEMPTS = 2;

    private final AppProperties.Whatsapp config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "order-notifier");
        t.setDaemon(true);
        return t;
    });

    public OrderNotifier(AppProperties props) {
        this.config = props.whatsapp();
        if (!config.enabled()) {
            log.info("WHATSAPP_PHONE / WHATSAPP_APIKEY not set: no WhatsApp message for new orders");
        }
    }

    /** Runs after the order's transaction commits; the request does not wait for WhatsApp. */
    @TransactionalEventListener
    public void onOrderPlaced(OrderPlaced order) {
        if (!config.enabled()) return;
        String text = message(order);
        sender.execute(() -> send(text));
    }

    static String message(OrderPlaced o) {
        StringBuilder b = new StringBuilder()
                .append("🛍️ *Νέα παραγγελία* – ").append(euro(o.total())).append('\n')
                .append("👤 ").append(o.name()).append('\n')
                .append("📞 ").append(o.phone()).append('\n')
                .append("✉️ ").append(o.email()).append('\n')
                .append("📍 ").append(o.address()).append("\n\n");
        for (OrderPlaced.Line l : o.items()) {
            b.append("• ").append(l.name()).append(" ×").append(l.qty())
                    .append(" — ").append(euro(l.lineTotal())).append('\n');
        }
        b.append(o.shipping().signum() > 0 ? "Μεταφορικά: " + euro(o.shipping()) : "Δωρεάν μεταφορικά").append('\n');
        if (o.notes() != null && !o.notes().isBlank()) {
            b.append("\n📝 ").append(o.notes()).append('\n');
        }
        String body = b.toString();
        String footer = "\n" + ADMIN_URL;
        if (body.length() + footer.length() > MAX_TEXT) {
            body = body.substring(0, MAX_TEXT - footer.length() - 2) + "…\n";
        }
        return body + footer;
    }

    private void send(String text) {
        URI uri = URI.create(ENDPOINT + "?phone=" + enc(config.phone().trim())
                + "&text=" + enc(text) + "&apikey=" + enc(config.apikey().trim()));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 == 2) return;
                log.warn("WhatsApp order alert failed (attempt {}): HTTP {}", attempt, res.statusCode());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("WhatsApp order alert failed (attempt {}): {}", attempt, e.toString());
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

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String euro(BigDecimal amount) {
        return "€" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

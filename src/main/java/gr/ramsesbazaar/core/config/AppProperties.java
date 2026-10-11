package gr.ramsesbazaar.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties("app")
public record AppProperties(String sessionSecret, String adminPassword, Cors cors, Whatsapp whatsapp) {

    public AppProperties {
        if (cors == null) cors = new Cors(List.of(), null);
        if (whatsapp == null) whatsapp = new Whatsapp(null, null);
    }

    /** CallMeBot recipient for new-order alerts. Both empty turns the alerts off. */
    public record Whatsapp(String phone, String apikey) {

        public boolean enabled() {
            return phone != null && !phone.isBlank() && apikey != null && !apikey.isBlank();
        }
    }

    public record Cors(List<String> allowedOrigins, String extraOrigins) {

        public List<String> all() {
            List<String> out = new ArrayList<>(allowedOrigins == null ? List.of() : allowedOrigins);
            if (extraOrigins != null) {
                Arrays.stream(extraOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(out::add);
            }
            return out;
        }
    }

    public boolean hasSessionSecret() {
        return sessionSecret != null && !sessionSecret.isBlank();
    }

    public boolean hasAdminPassword() {
        return adminPassword != null && !adminPassword.isEmpty();
    }
}

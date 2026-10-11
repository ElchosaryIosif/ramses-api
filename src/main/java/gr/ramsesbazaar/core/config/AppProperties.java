package gr.ramsesbazaar.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties("app")
public record AppProperties(String sessionSecret, String adminPassword, Cors cors) {

    public AppProperties {
        if (cors == null) cors = new Cors(List.of(), null);
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

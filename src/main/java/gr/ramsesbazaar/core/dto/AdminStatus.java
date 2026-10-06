package gr.ramsesbazaar.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminStatus", description = "Warnings the admin panel shows until they are fixed")
public record AdminStatus(
        @Schema(description = "False while SESSION_SECRET is unset and sessions use a per-boot key")
        boolean secure,
        @Schema(description = "True while the admin password is still the public default")
        boolean defaultPassword
) {
}

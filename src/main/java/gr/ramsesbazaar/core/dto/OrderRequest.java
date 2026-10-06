package gr.ramsesbazaar.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "OrderRequest", description = "A customer's order. Prices are taken from the catalogue, not from the request.")
public record OrderRequest(
        String name,
        String phone,
        String email,
        String address,
        String notes,
        List<Line> items,
        @Schema(description = "Honeypot: left empty by people, filled in by bots")
        String website
) {

    public record Line(String id, Integer qty) {
    }
}

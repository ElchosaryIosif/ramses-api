package gr.ramsesbazaar.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "Catalogue", description = "Every product, in display order, with the revision it was read at")
public record CatalogueDto(int rev, List<ProductDto> products) {
}

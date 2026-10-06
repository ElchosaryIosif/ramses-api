package gr.ramsesbazaar.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SaveCatalogueRequest", description = "The full new product list and the revision it was edited from")
public record SaveCatalogueRequest(Integer baseRev, List<ProductDto> products) {
}

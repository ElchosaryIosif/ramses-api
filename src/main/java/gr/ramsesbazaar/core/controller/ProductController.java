package gr.ramsesbazaar.core.controller;

import gr.ramsesbazaar.core.dto.CatalogueDto;
import gr.ramsesbazaar.core.dto.SaveCatalogueRequest;
import gr.ramsesbazaar.core.security.RequireAdmin;
import gr.ramsesbazaar.core.service.CatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "The shop catalogue")
public class ProductController {

    private final CatalogueService catalogueService;

    @Operation(summary = "Get the catalogue", description = "Every product in display order, with the current revision.")
    @GetMapping
    public CatalogueDto get() {
        return catalogueService.get();
    }

    @Operation(summary = "Replace the catalogue",
            description = "Saves the full product list. `baseRev` must equal the current revision; otherwise the "
                    + "answer is 409 with the current catalogue, so the client can re-apply its edits and retry.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saved"),
            @ApiResponse(responseCode = "401", description = "Not signed in"),
            @ApiResponse(responseCode = "409", description = "Someone saved in the meantime"),
            @ApiResponse(responseCode = "413", description = "An image is larger than 4 MB"),
            @ApiResponse(responseCode = "422", description = "A product is invalid")
    })
    @RequireAdmin
    @PutMapping
    public CatalogueDto save(@RequestBody SaveCatalogueRequest request) {
        return catalogueService.save(request.baseRev(), request.products());
    }
}

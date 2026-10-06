package gr.ramsesbazaar.core.controller;

import gr.ramsesbazaar.core.dto.SaveSettingsRequest;
import gr.ramsesbazaar.core.dto.SettingsDto;
import gr.ramsesbazaar.core.security.RequireAdmin;
import gr.ramsesbazaar.core.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Shop contact and bank-transfer details")
public class SettingsController {

    private final SettingsService settingsService;

    @Operation(summary = "Get the public shop settings")
    @GetMapping
    public SettingsDto get() {
        return settingsService.publicSettings();
    }

    @Operation(summary = "Update shop settings", description = "Only the fields present in the request change.")
    @RequireAdmin
    @PutMapping
    public SettingsDto save(@RequestBody SaveSettingsRequest request) {
        return settingsService.update(request.settings());
    }
}

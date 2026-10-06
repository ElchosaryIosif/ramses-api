package gr.ramsesbazaar.core.controller;

import gr.ramsesbazaar.core.dto.ChangePasswordRequest;
import gr.ramsesbazaar.core.dto.LoginRequest;
import gr.ramsesbazaar.core.dto.SessionGrant;
import gr.ramsesbazaar.core.dto.SessionStatus;
import gr.ramsesbazaar.core.security.RequireAdmin;
import gr.ramsesbazaar.core.service.AuthService;
import gr.ramsesbazaar.core.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Admin session", description = "Signing in to the admin panel")
public class AuthController {

    private final AuthService authService;
    private final SettingsService settingsService;

    @Operation(summary = "Sign in", description = "Returns a bearer token for the admin endpoints.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in"),
            @ApiResponse(responseCode = "401", description = "Wrong password")
    })
    @PostMapping("/login")
    public SessionGrant login(@RequestBody LoginRequest request) {
        return authService.login(request.password(), !Boolean.FALSE.equals(request.remember()));
    }

    @Operation(summary = "Check the current session")
    @RequireAdmin
    @GetMapping("/session")
    public SessionStatus session() {
        return new SessionStatus(settingsService.adminStatus(settingsService.current()));
    }

    @Operation(summary = "Change the admin password", description = "Signs out every other device.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Changed; a fresh token is returned"),
            @ApiResponse(responseCode = "403", description = "Current password is wrong"),
            @ApiResponse(responseCode = "422", description = "New password is shorter than 8 characters")
    })
    @RequireAdmin
    @PostMapping("/password")
    public SessionGrant changePassword(@RequestBody ChangePasswordRequest request) {
        return authService.changePassword(request.current(), request.next());
    }
}

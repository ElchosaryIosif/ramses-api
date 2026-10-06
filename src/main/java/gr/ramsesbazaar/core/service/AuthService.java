package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.ApiException;
import gr.ramsesbazaar.core.dto.SessionGrant;
import gr.ramsesbazaar.core.entity.ShopSettings;
import gr.ramsesbazaar.core.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long WRONG_PASSWORD_DELAY_MS = 800;

    private final SettingsService settingsService;
    private final TokenService tokenService;

    public SessionGrant login(String password, boolean remember) {
        ShopSettings s = settingsService.current();
        if (!settingsService.passwordMatches(s, password)) {
            slowDown();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "wrong_password");
        }
        return grant(s, remember);
    }

    public SessionGrant changePassword(String current, String next) {
        ShopSettings s = settingsService.current();
        if (!settingsService.passwordMatches(s, current)) {
            slowDown();
            throw new ApiException(HttpStatus.FORBIDDEN, "wrong_password");
        }
        if (next == null || next.length() < SettingsService.MIN_PASSWORD_LENGTH) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "weak_password");
        }
        return grant(settingsService.changePassword(next), true);
    }

    private SessionGrant grant(ShopSettings s, boolean remember) {
        TokenService.Issued issued = tokenService.issue(s.getPasswordVersion(), remember);
        return new SessionGrant(issued.token(), issued.exp(), settingsService.adminStatus(s));
    }

    private static void slowDown() {
        try {
            Thread.sleep(WRONG_PASSWORD_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

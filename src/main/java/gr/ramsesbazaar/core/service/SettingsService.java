package gr.ramsesbazaar.core.service;

import gr.ramsesbazaar.core.config.AppProperties;
import gr.ramsesbazaar.core.dto.AdminStatus;
import gr.ramsesbazaar.core.dto.SettingsDto;
import gr.ramsesbazaar.core.entity.Catalogue;
import gr.ramsesbazaar.core.entity.ShopSettings;
import gr.ramsesbazaar.core.repository.CatalogueRepository;
import gr.ramsesbazaar.core.repository.ShopSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService implements ApplicationRunner {

    /** Shipped as the default in the original prototype, so it is public knowledge. */
    public static final String DEFAULT_PASSWORD = "ramses1996";
    public static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_FIELD_LENGTH = 300;

    private final ShopSettingsRepository settingsRepository;
    private final CatalogueRepository catalogueRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    /** bcrypt is deliberately slow; remember the answer per password version. */
    private volatile int defaultCheckedVersion = -1;
    private volatile boolean defaultCheckedResult;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!catalogueRepository.existsById(Catalogue.SINGLETON_ID)) {
            catalogueRepository.save(new Catalogue());
        }
        ShopSettings s = settingsRepository.findById(ShopSettings.SINGLETON_ID).orElseGet(ShopSettings::new);
        if (s.getPasswordHash() == null) {
            String initial = props.hasAdminPassword() ? props.adminPassword() : DEFAULT_PASSWORD;
            s.setPasswordHash(passwordEncoder.encode(initial));
            if (!props.hasAdminPassword()) {
                log.warn("Admin password initialised to the public default; change it in the admin panel");
            }
        }
        settingsRepository.save(s);
    }

    @Transactional(readOnly = true)
    public ShopSettings current() {
        return settingsRepository.findById(ShopSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("shop_settings row missing"));
    }

    @Transactional(readOnly = true)
    public int passwordVersion() {
        return current().getPasswordVersion();
    }

    @Transactional(readOnly = true)
    public SettingsDto publicSettings() {
        return SettingsDto.from(current());
    }

    @Transactional
    public SettingsDto update(SettingsDto patch) {
        ShopSettings s = current();
        if (patch != null) {
            apply(patch.whatsapp(), s::setWhatsapp);
            apply(patch.phone(), s::setPhone);
            apply(patch.address(), s::setAddress);
            apply(patch.iban(), s::setIban);
            apply(patch.bank(), s::setBank);
            apply(patch.accountName(), s::setAccountName);
            apply(patch.email(), s::setEmail);
        }
        return SettingsDto.from(settingsRepository.save(s));
    }

    private static void apply(String value, Consumer<String> setter) {
        if (value == null) return;
        String v = value.trim();
        setter.accept(v.length() > MAX_FIELD_LENGTH ? v.substring(0, MAX_FIELD_LENGTH) : v);
    }

    /** ADMIN_PASSWORD always works, so a forgotten password can be recovered from Railway. */
    public boolean passwordMatches(ShopSettings s, String password) {
        if (password == null || password.isEmpty()) return false;
        if (props.hasAdminPassword() && MessageDigest.isEqual(
                password.getBytes(StandardCharsets.UTF_8), props.adminPassword().getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        return s.getPasswordHash() != null && passwordEncoder.matches(password, s.getPasswordHash());
    }

    @Transactional
    public ShopSettings changePassword(String next) {
        ShopSettings s = current();
        s.setPasswordHash(passwordEncoder.encode(next));
        s.setPasswordVersion(s.getPasswordVersion() + 1);
        return settingsRepository.save(s);
    }

    public AdminStatus adminStatus(ShopSettings s) {
        if (defaultCheckedVersion != s.getPasswordVersion()) {
            defaultCheckedResult = s.getPasswordHash() != null
                    && passwordEncoder.matches(DEFAULT_PASSWORD, s.getPasswordHash());
            defaultCheckedVersion = s.getPasswordVersion();
        }
        return new AdminStatus(props.hasSessionSecret(), defaultCheckedResult);
    }
}

package gr.ramsesbazaar.core.repository;

import gr.ramsesbazaar.core.entity.ShopSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, Short> {
}

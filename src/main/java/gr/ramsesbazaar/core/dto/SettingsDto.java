package gr.ramsesbazaar.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import gr.ramsesbazaar.core.entity.ShopSettings;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Settings", description = "Public shop contact and bank-transfer details")
public record SettingsDto(
        String whatsapp,
        String phone,
        String address,
        String iban,
        String bank,
        String accountName,
        String email
) {

    public static SettingsDto from(ShopSettings s) {
        return new SettingsDto(s.getWhatsapp(), s.getPhone(), s.getAddress(), s.getIban(), s.getBank(),
                s.getAccountName(), s.getEmail());
    }
}

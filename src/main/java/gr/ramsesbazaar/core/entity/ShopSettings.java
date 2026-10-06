package gr.ramsesbazaar.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shop_settings")
@Getter
@Setter
@NoArgsConstructor
public class ShopSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    private String whatsapp;
    private String phone;
    private String address;
    private String iban;
    private String bank;
    private String accountName;
    private String email;

    private String passwordHash;

    /** Bumped on every password change; tokens carry it, so old sessions stop working. */
    @Column(nullable = false)
    private int passwordVersion;
}

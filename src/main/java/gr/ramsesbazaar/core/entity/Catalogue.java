package gr.ramsesbazaar.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "catalogue")
@Getter
@Setter
@NoArgsConstructor
public class Catalogue {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(nullable = false)
    private int rev;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}

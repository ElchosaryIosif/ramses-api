package gr.ramsesbazaar.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "product_image")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    private String imageKey;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

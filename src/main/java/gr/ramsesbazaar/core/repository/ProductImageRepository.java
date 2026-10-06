package gr.ramsesbazaar.core.repository;

import gr.ramsesbazaar.core.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {

    @Query("select i.imageKey from ProductImage i")
    List<String> findAllKeys();
}

package gr.ramsesbazaar.core.repository;

import gr.ramsesbazaar.core.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findAllByOrderByPositionAsc();
}

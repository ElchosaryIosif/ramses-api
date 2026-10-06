package gr.ramsesbazaar.core.repository;

import gr.ramsesbazaar.core.entity.ShopOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, UUID> {

    List<ShopOrder> findAllByOrderByCreatedAtDesc(Pageable page);
}

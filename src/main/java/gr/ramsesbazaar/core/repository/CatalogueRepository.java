package gr.ramsesbazaar.core.repository;

import gr.ramsesbazaar.core.entity.Catalogue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CatalogueRepository extends JpaRepository<Catalogue, Short> {

    /** Serialises concurrent saves: the second waits, then sees the bumped revision. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Catalogue c where c.id = 1")
    Optional<Catalogue> lockSingleton();
}

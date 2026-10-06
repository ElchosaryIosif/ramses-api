package gr.ramsesbazaar.core.config;

import gr.ramsesbazaar.core.dto.CatalogueDto;
import lombok.Getter;

/** The save was based on an old revision; carries the current catalogue so the client can rebase. */
@Getter
public class CatalogueConflictException extends RuntimeException {

    private final CatalogueDto current;

    public CatalogueConflictException(CatalogueDto current) {
        super("conflict");
        this.current = current;
    }
}

package gr.ramsesbazaar.core.config;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** An expected failure, answered as {@code {"error": code}} with the given status. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}

package gr.ramsesbazaar.core.dto;

public record SessionGrant(String token, long exp, AdminStatus status) {
}

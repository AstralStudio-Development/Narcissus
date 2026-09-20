package cn.starry.narcissus.functions.auth;

/** Result of one authentication/account operation. */
public final class AuthenticationResult {
    public enum Status {
        REGISTERED,
        STAFF_REGISTERED,
        LOGIN_SUCCESS,
        TWO_FACTOR_SETUP_READY,
        TWO_FACTOR_ENABLED,
        ALREADY_REGISTERED,
        NOT_REGISTERED,
        INVALID_PASSWORD,
        STAFF_CODE_REQUIRED,
        INVALID_STAFF_CODE,
        TWO_FACTOR_NOT_CONFIGURED,
        INVALID_TWO_FACTOR,
        NOT_STAFF_ACCOUNT,
        STORAGE_ERROR
    }

    private final Status status;
    private final String value;
    private final String secondaryValue;

    private AuthenticationResult(Status status, String value, String secondaryValue) {
        this.status = status;
        this.value = value;
        this.secondaryValue = secondaryValue;
    }

    public static AuthenticationResult of(Status status) {
        return new AuthenticationResult(status, null, null);
    }

    public static AuthenticationResult of(Status status, String value) {
        return new AuthenticationResult(status, value, null);
    }

    public static AuthenticationResult of(Status status, String value, String secondaryValue) {
        return new AuthenticationResult(status, value, secondaryValue);
    }

    public Status getStatus() {
        return this.status;
    }

    /** Primary payload, such as a generated TOTP secret. */
    public String getValue() {
        return this.value;
    }

    /** Secondary payload, such as an otpauth URI. */
    public String getSecondaryValue() {
        return this.secondaryValue;
    }
}

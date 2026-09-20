package cn.starry.narcissus.functions.auth;

/** Storage-backed account operations used by the protocol-independent core. */
public interface AuthenticationProvider {
    AuthenticationResult register(String username, String credential);

    AuthenticationResult login(String username, String credential);

    AuthenticationResult beginTwoFactorSetup(String username);

    AuthenticationResult confirmTwoFactor(String username, String code);

    boolean isRegistered(String username);

    boolean isStaffAccount(String username);

    boolean isTwoFactorConfigured(String username);
}

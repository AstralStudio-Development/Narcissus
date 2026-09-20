package cn.starry.narcissus.functions.auth;

/** Bridges Narcissus authentication to the current BungeeCord session. */
public interface AuthenticationSessionHandler {
    boolean isAuthenticated(String username);

    void onAuthenticated(String username);
}

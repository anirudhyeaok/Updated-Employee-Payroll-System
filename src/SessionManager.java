import com.sun.net.httpserver.HttpExchange;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private static final String COOKIE_NAME = "paycore_session";
    private static final long SESSION_TTL_SECONDS = 8 * 60 * 60;
    private static final Map<String, SessionData> SESSIONS = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    public static SessionData createSession(String userId, String username, String role) {
        String token = UUID.randomUUID().toString();
        SessionData session = new SessionData(token, userId, username, role, Instant.now().plusSeconds(SESSION_TTL_SECONDS));
        SESSIONS.put(token, session);
        return session;
    }

    public static SessionData getSession(HttpExchange exchange) {
        String token = readCookie(exchange, COOKIE_NAME);
        if (token == null || token.isBlank()) {
            return null;
        }

        SessionData session = SESSIONS.get(token);
        if (session == null) {
            return null;
        }

        if (session.expiresAt().isBefore(Instant.now())) {
            SESSIONS.remove(token);
            return null;
        }

        return session;
    }

    public static void destroySession(HttpExchange exchange) {
        String token = readCookie(exchange, COOKIE_NAME);
        if (token != null) {
            SESSIONS.remove(token);
        }
    }

    public static void attachSessionCookie(HttpExchange exchange, SessionData session) {
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                COOKIE_NAME + "=" + session.token() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + SESSION_TTL_SECONDS
        );
    }

    public static void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
        );
    }

    private static String readCookie(HttpExchange exchange, String cookieName) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }

        for (String header : cookieHeaders) {
            String[] cookies = header.split(";");
            for (String cookie : cookies) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && cookieName.equals(pair[0].trim())) {
                    return pair[1].trim();
                }
            }
        }
        return null;
    }

    public record SessionData(String token, String userId, String username, String role, Instant expiresAt) {
    }
}

import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpUtil {

    private HttpUtil() {
    }

    public static void applyJsonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
    }

    public static boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            applyJsonHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    public static JSONObject readJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new JSONObject(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static void sendJson(HttpExchange exchange, int code, JSONObject body) throws IOException {
        sendText(exchange, code, body.toString(), "application/json; charset=UTF-8");
    }

    public static void sendJsonArray(HttpExchange exchange, int code, String body) throws IOException {
        sendText(exchange, code, body, "application/json; charset=UTF-8");
    }

    public static void sendCsv(HttpExchange exchange, String filename, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static SessionManager.SessionData requireSession(HttpExchange exchange) throws IOException {
        SessionManager.SessionData session = SessionManager.getSession(exchange);
        if (session == null) {
            JSONObject body = new JSONObject();
            body.put("success", false);
            body.put("message", "Your session has expired. Please sign in again.");
            sendJson(exchange, 401, body);
        }
        return session;
    }

    public static SessionManager.SessionData requireRole(HttpExchange exchange, String role) throws IOException {
        SessionManager.SessionData session = requireSession(exchange);
        if (session == null) {
            return null;
        }

        if (!role.equalsIgnoreCase(session.role())) {
            JSONObject body = new JSONObject();
            body.put("success", false);
            body.put("message", "You are not authorised to access this module.");
            sendJson(exchange, 403, body);
            return null;
        }

        return session;
    }

    private static void sendText(HttpExchange exchange, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

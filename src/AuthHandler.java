import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

public class AuthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        HttpUtil.applyJsonHeaders(exchange);
        if (HttpUtil.handleOptions(exchange)) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("POST".equalsIgnoreCase(method) && "/api/login".equals(path)) {
                handleLogin(exchange);
            } else if ("POST".equalsIgnoreCase(method) && "/api/logout".equals(path)) {
                handleLogout(exchange);
            } else if ("GET".equalsIgnoreCase(method) && "/api/session".equals(path)) {
                handleCurrentSession(exchange);
            } else {
                JSONObject response = new JSONObject();
                response.put("success", false);
                response.put("message", "Not found");
                HttpUtil.sendJson(exchange, 404, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Authentication service error.");
            HttpUtil.sendJson(exchange, 500, response);
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        JSONObject requestJson = HttpUtil.readJsonBody(exchange);
        String username = requestJson.optString("username", "").trim();
        String password = requestJson.optString("password", "").trim();

        if (username.isEmpty() || password.isEmpty()) {
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Username and password are required.");
            HttpUtil.sendJson(exchange, 400, response);
            return;
        }

        String[] userData = Database.authenticateUser(username, password);
        JSONObject response = new JSONObject();

        if (userData == null) {
            response.put("success", false);
            response.put("message", "Invalid credentials");
            HttpUtil.sendJson(exchange, 401, response);
            return;
        }

        SessionManager.SessionData session = SessionManager.createSession(userData[0], userData[1], userData[2]);
        SessionManager.attachSessionCookie(exchange, session);

        response.put("success", true);
        response.put("userId", userData[0]);
        response.put("username", userData[1]);
        response.put("role", userData[2]);
        HttpUtil.sendJson(exchange, 200, response);
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        SessionManager.destroySession(exchange);
        SessionManager.clearSessionCookie(exchange);

        JSONObject response = new JSONObject();
        response.put("success", true);
        HttpUtil.sendJson(exchange, 200, response);
    }

    private void handleCurrentSession(HttpExchange exchange) throws IOException {
        SessionManager.SessionData session = SessionManager.getSession(exchange);
        JSONObject response = new JSONObject();

        if (session == null) {
            response.put("success", false);
            response.put("message", "No active session");
            HttpUtil.sendJson(exchange, 401, response);
            return;
        }

        response.put("success", true);
        response.put("userId", session.userId());
        response.put("username", session.username());
        response.put("role", session.role());
        HttpUtil.sendJson(exchange, 200, response);
    }
}

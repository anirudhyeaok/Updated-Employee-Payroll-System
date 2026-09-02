import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;

public class EmployeeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        HttpUtil.applyJsonHeaders(exchange);
        if (HttpUtil.handleOptions(exchange)) {
            return;
        }

        SessionManager.SessionData session = HttpUtil.requireRole(exchange, "employee");
        if (session == null) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        int userId = Integer.parseInt(session.userId());

        try {
            if ("GET".equalsIgnoreCase(method) && "/api/employee/payslips".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getPayslipsForUser(userId).toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/employee/profile".equals(path)) {
                HttpUtil.sendJson(exchange, 200, Database.getEmployeeProfileByUserId(userId));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/employee/revisions".equals(path)) {
                JSONObject profile = Database.getEmployeeProfileByUserId(userId);
                HttpUtil.sendJsonArray(exchange, 200, Database.getEmployeeRevisionHistory(profile.getInt("empId")).toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/employee/complaints".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getComplaintsForUser(userId).toString());
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/employee/profile".equals(path)) {
                handleProfileUpdate(exchange, userId);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/employee/complaints".equals(path)) {
                handleCreateComplaint(exchange, userId);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Not found");
            HttpUtil.sendJson(exchange, 404, response);
        } catch (Exception e) {
            e.printStackTrace();
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Employee service error.");
            HttpUtil.sendJson(exchange, 500, response);
        }
    }

    private void handleProfileUpdate(HttpExchange exchange, int userId) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        JSONObject profile = Database.getEmployeeProfileByUserId(userId);
        JSONObject updates = new JSONObject();

        if (req.has("bankAccount")) updates.put("bankAccount", req.optString("bankAccount", ""));
        if (req.has("email")) updates.put("email", req.optString("email", ""));
        if (req.has("phone")) updates.put("phone", req.optString("phone", ""));
        if (req.has("address")) updates.put("address", req.optString("address", ""));

        boolean success = Database.updateEmployeeProfile(profile.getInt("empId"), updates, userId);
        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Profile updated successfully." : "Could not update profile.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private void handleCreateComplaint(HttpExchange exchange, int userId) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        boolean success = Database.createComplaint(
                userId,
                req.optString("monthYear", ""),
                req.optString("subject", ""),
                req.optString("message", "")
        );

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Complaint submitted to payroll admin." : "Could not submit complaint.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }
}

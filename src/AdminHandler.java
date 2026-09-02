import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class AdminHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        HttpUtil.applyJsonHeaders(exchange);
        if (HttpUtil.handleOptions(exchange)) {
            return;
        }

        SessionManager.SessionData session = HttpUtil.requireRole(exchange, "admin");
        if (session == null) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equalsIgnoreCase(method) && "/api/admin/employees".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getAllEmployees().toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/admins".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getAllAdmins().toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/adjustments".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getPayrollAdjustments().toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/adjustments/pending".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getPendingPayrollAdjustments().toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/payroll-history".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getPayrollHistory().toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/complaints".equals(path)) {
                HttpUtil.sendJsonArray(exchange, 200, Database.getAllComplaints().toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/employee-profile".equals(path)) {
                int empId = parseRequiredIntQuery(exchange, "empId");
                HttpUtil.sendJson(exchange, 200, Database.getEmployeeProfileByEmpId(empId));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/revisions".equals(path)) {
                int empId = parseRequiredIntQuery(exchange, "empId");
                HttpUtil.sendJsonArray(exchange, 200, Database.getEmployeeRevisionHistory(empId).toString());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/analytics/departments".equals(path)) {
                String month = readQueryParam(exchange, "month");
                HttpUtil.sendJson(exchange, 200, Database.getDepartmentAnalytics(month));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/export/payroll".equals(path)) {
                String month = readQueryParam(exchange, "month");
                HttpUtil.sendCsv(exchange, buildFilename("payroll-register", month), Database.exportPayrollCsv(month));
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/export/employees".equals(path)) {
                HttpUtil.sendCsv(exchange, "employee-master.csv", Database.exportEmployeeMasterCsv());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/export/monthly-summary".equals(path)) {
                HttpUtil.sendCsv(exchange, "monthly-summary.csv", Database.exportMonthlySummaryCsv());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/export/quarterly-summary".equals(path)) {
                HttpUtil.sendCsv(exchange, "quarterly-summary.csv", Database.exportQuarterlySummaryCsv());
                return;
            }
            if ("GET".equalsIgnoreCase(method) && "/api/admin/export/yearly-summary".equals(path)) {
                HttpUtil.sendCsv(exchange, "yearly-summary.csv", Database.exportYearlySummaryCsv());
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/add-employee".equals(path)) {
                handleAddEmployee(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/add-admin".equals(path)) {
                handleAddAdmin(exchange);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/add-adjustment".equals(path)) {
                handleAddAdjustment(exchange, session);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/adjustments/review".equals(path)) {
                handleReviewAdjustment(exchange, session);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/payroll".equals(path)) {
                JSONObject request = HttpUtil.readJsonBody(exchange);
                HttpUtil.sendJson(exchange, 200, Database.runPayroll(request.optString("monthYear", "")));
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/complaints/reply".equals(path)) {
                handleReplyToComplaint(exchange, session);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && "/api/admin/employee-profile/update".equals(path)) {
                handleUpdateEmployeeProfile(exchange, session);
                return;
            }
            
            // ── NEW: DELETE EMPLOYEE ENDPOINT ──
            if ("POST".equalsIgnoreCase(method) && "/api/admin/delete-employee".equals(path)) {
                handleDeleteEmployee(exchange);
                return;
            }

            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Not found");
            HttpUtil.sendJson(exchange, 404, response);
        } catch (IllegalArgumentException e) {
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", e.getMessage());
            HttpUtil.sendJson(exchange, 400, response);
        } catch (Exception e) {
            e.printStackTrace();
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("message", "Admin service error.");
            HttpUtil.sendJson(exchange, 500, response);
        }
    }

    private void handleAddEmployee(HttpExchange exchange) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        boolean success = Database.addEmployee(
                req.optString("username", ""),
                req.optString("password", ""),
                req.optString("fullName", ""),
                req.optString("department", ""),
                req.optString("designation", ""),
                req.optDouble("baseSalary", 0)
        );

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Employee created successfully." : "Could not create employee.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    // ── NEW: DELETE EMPLOYEE HANDLER ──
    private void handleDeleteEmployee(HttpExchange exchange) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        int empId = req.optInt("empId", -1);
        boolean success = false;
        String message = "Invalid employee ID.";

        if (empId != -1) {
            success = Database.deleteEmployee(empId);
            message = success ? "Employee deleted successfully." : "Could not delete employee.";
        }

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", message);
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private void handleAddAdmin(HttpExchange exchange) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        boolean success = Database.addAdmin(req.optString("username", ""), req.optString("password", ""));

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Admin account created." : "Could not create admin account.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private void handleAddAdjustment(HttpExchange exchange, SessionManager.SessionData session) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        boolean success = Database.addPayrollAdjustment(
                req.getInt("empId"),
                req.optString("monthYear", ""),
                req.optString("type", "EARNING"),
                req.optDouble("amount", 0),
                req.optString("reason", ""),
                Integer.parseInt(session.userId())
        );

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Adjustment added and applied — it will be included in the next payroll run." : "Could not save adjustment.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private void handleReviewAdjustment(HttpExchange exchange, SessionManager.SessionData session) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        boolean success = Database.reviewPayrollAdjustment(
                req.getInt("adjustmentId"),
                req.optString("status", "Rejected"),
                req.optString("adminComment", ""),
                Integer.parseInt(session.userId())
        );

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Adjustment review saved." : "Could not review adjustment.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private void handleReplyToComplaint(HttpExchange exchange, SessionManager.SessionData session) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        boolean success = Database.replyToComplaint(
                req.getInt("complaintId"),
                req.optString("reply", ""),
                Integer.parseInt(session.userId())
        );

        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Reply sent to employee." : "Could not send reply.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private void handleUpdateEmployeeProfile(HttpExchange exchange, SessionManager.SessionData session) throws IOException {
        JSONObject req = HttpUtil.readJsonBody(exchange);
        int empId = req.getInt("empId");
        JSONObject updates = req.getJSONObject("updates");

        boolean success = Database.updateEmployeeProfile(empId, updates, Integer.parseInt(session.userId()));
        JSONObject response = new JSONObject();
        response.put("success", success);
        response.put("message", success ? "Employee profile updated." : "Could not update employee profile.");
        HttpUtil.sendJson(exchange, success ? 200 : 400, response);
    }

    private int parseRequiredIntQuery(HttpExchange exchange, String key) {
        String raw = readQueryParam(exchange, key);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return Integer.parseInt(raw);
    }

    private String readQueryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] valuePair = pair.split("=", 2);
            String candidate = URLDecoder.decode(valuePair[0], StandardCharsets.UTF_8);
            if (key.equals(candidate)) {
                return valuePair.length > 1 ? URLDecoder.decode(valuePair[1], StandardCharsets.UTF_8) : "";
            }
        }
        return null;
    }

    private String buildFilename(String prefix, String month) {
        if (month == null || month.isBlank()) {
            return prefix + "-all-periods.csv";
        }
        return prefix + "-" + month.trim().replaceAll("[^A-Za-z0-9]+", "-").toLowerCase(Locale.ENGLISH) + ".csv";
    }
}
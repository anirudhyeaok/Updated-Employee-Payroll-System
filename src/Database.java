import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Database.java
 * Centralises JDBC access, schema initialisation, authentication,
 * and payroll calculations for the PayCore Payroll System.
 */
public class Database {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/payroll_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "2111";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int HASH_ITERATIONS = 65_536;
    private static final int HASH_KEY_LENGTH = 256;

    private static final DateTimeFormatter DISPLAY_MONTH_FORMAT =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMMM uuuu")
                    .toFormatter(Locale.ENGLISH);

    private static final DateTimeFormatter[] PAYROLL_INPUT_FORMATS = new DateTimeFormatter[]{
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMMM uuuu").toFormatter(Locale.ENGLISH),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM uuuu").toFormatter(Locale.ENGLISH),
            DateTimeFormatter.ofPattern("uuuu-MM")
    };

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void init() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "username VARCHAR(100) NOT NULL UNIQUE," +
                            "password VARCHAR(255) NOT NULL," +
                            "password_salt VARCHAR(64) DEFAULT NULL," +
                            "role VARCHAR(50) NOT NULL" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS employees (" +
                            "emp_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "user_id INT NOT NULL," +
                            "full_name VARCHAR(200) NOT NULL," +
                            "department VARCHAR(100) NOT NULL," +
                            "designation VARCHAR(100) NOT NULL," +
                            "base_salary DOUBLE NOT NULL," +
                            "employment_status VARCHAR(30) NOT NULL DEFAULT 'Active'," +
                            "bank_account VARCHAR(50) DEFAULT 'Pending'," +
                            "email VARCHAR(150) DEFAULT 'Pending'," +
                            "phone VARCHAR(30) DEFAULT 'Pending'," +
                            "address VARCHAR(255) DEFAULT 'Pending'," +
                            "joining_date DATE DEFAULT NULL," +
                            "FOREIGN KEY (user_id) REFERENCES users(id)" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS leaves (" +
                            "leave_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "emp_id INT NOT NULL," +
                            "start_date DATE NOT NULL," +
                            "end_date DATE NOT NULL," +
                            "days_count DOUBLE NOT NULL DEFAULT 1," +
                            "leave_type VARCHAR(30) NOT NULL DEFAULT 'PAID'," +
                            "reason VARCHAR(255)," +
                            "status VARCHAR(20) NOT NULL DEFAULT 'Pending'," +
                            "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (emp_id) REFERENCES employees(emp_id)" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS payslips (" +
                            "slip_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "emp_id INT NOT NULL," +
                            "month_year VARCHAR(20) NOT NULL," +
                            "base_pay DOUBLE NOT NULL," +
                            "hra DOUBLE NOT NULL DEFAULT 0," +
                            "da DOUBLE NOT NULL DEFAULT 0," +
                            "ta DOUBLE NOT NULL DEFAULT 0," +
                            "allowances DOUBLE NOT NULL DEFAULT 0," +
                            "gross_pay DOUBLE NOT NULL DEFAULT 0," +
                            "pf_deduction DOUBLE NOT NULL DEFAULT 0," +
                            "professional_tax DOUBLE NOT NULL DEFAULT 0," +
                            "lop_deduction DOUBLE NOT NULL DEFAULT 0," +
                            "adjustment_earnings DOUBLE NOT NULL DEFAULT 0," +
                            "adjustment_deductions DOUBLE NOT NULL DEFAULT 0," +
                            "deductions DOUBLE NOT NULL DEFAULT 0," +
                            "total_deductions DOUBLE NOT NULL DEFAULT 0," +
                            "lop_days DOUBLE NOT NULL DEFAULT 0," +
                            "working_days INT NOT NULL DEFAULT 30," +
                            "net_salary DOUBLE NOT NULL," +
                            "generated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (emp_id) REFERENCES employees(emp_id)" +
                    ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS payroll_adjustments (" +
                            "adjustment_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "emp_id INT NOT NULL," +
                            "month_year VARCHAR(20) NOT NULL," +
                            "adjustment_type VARCHAR(20) NOT NULL," +
                            "amount DOUBLE NOT NULL," +
                            "reason VARCHAR(255) NOT NULL," +
                            "status VARCHAR(20) NOT NULL DEFAULT 'Pending'," +
                            "requested_by_user_id INT DEFAULT NULL," +
                            "reviewed_by_user_id INT DEFAULT NULL," +
                            "admin_comment VARCHAR(255) DEFAULT NULL," +
                            "reviewed_at TIMESTAMP NULL DEFAULT NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (emp_id) REFERENCES employees(emp_id)" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS payroll_complaints (" +
                            "complaint_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "emp_id INT NOT NULL," +
                            "month_year VARCHAR(20) DEFAULT NULL," +
                            "subject VARCHAR(150) NOT NULL," +
                            "message VARCHAR(500) NOT NULL," +
                            "status VARCHAR(20) NOT NULL DEFAULT 'Open'," +
                            "admin_reply VARCHAR(500) DEFAULT NULL," +
                            "replied_by_user_id INT DEFAULT NULL," +
                            "replied_at TIMESTAMP NULL DEFAULT NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (emp_id) REFERENCES employees(emp_id)" +
                            ")"
            );

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS employee_revision_history (" +
                            "revision_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                            "emp_id INT NOT NULL," +
                            "changed_by_user_id INT NOT NULL," +
                            "change_summary VARCHAR(255) NOT NULL," +
                            "previous_data TEXT NOT NULL," +
                            "new_data TEXT NOT NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "FOREIGN KEY (emp_id) REFERENCES employees(emp_id)" +
                            ")"
            );

            ensureColumn(conn, "users", "password_salt", "ALTER TABLE users ADD COLUMN password_salt VARCHAR(64) DEFAULT NULL");
            ensureColumn(conn, "employees", "employment_status", "ALTER TABLE employees ADD COLUMN employment_status VARCHAR(30) NOT NULL DEFAULT 'Active'");
            ensureColumn(conn, "employees", "bank_account", "ALTER TABLE employees ADD COLUMN bank_account VARCHAR(50) DEFAULT 'Pending'");
            ensureColumn(conn, "employees", "email", "ALTER TABLE employees ADD COLUMN email VARCHAR(150) DEFAULT 'Pending'");
            ensureColumn(conn, "employees", "phone", "ALTER TABLE employees ADD COLUMN phone VARCHAR(30) DEFAULT 'Pending'");
            ensureColumn(conn, "employees", "address", "ALTER TABLE employees ADD COLUMN address VARCHAR(255) DEFAULT 'Pending'");
            ensureColumn(conn, "employees", "joining_date", "ALTER TABLE employees ADD COLUMN joining_date DATE DEFAULT NULL");

            ensureColumn(conn, "leaves", "start_date", "ALTER TABLE leaves ADD COLUMN start_date DATE");
            ensureColumn(conn, "leaves", "end_date", "ALTER TABLE leaves ADD COLUMN end_date DATE");
            ensureColumn(conn, "leaves", "days_count", "ALTER TABLE leaves ADD COLUMN days_count DOUBLE NOT NULL DEFAULT 1");
            ensureColumn(conn, "leaves", "leave_type", "ALTER TABLE leaves ADD COLUMN leave_type VARCHAR(30) NOT NULL DEFAULT 'PAID'");
            ensureColumn(conn, "leaves", "reason", "ALTER TABLE leaves ADD COLUMN reason VARCHAR(255)");
            ensureColumn(conn, "leaves", "status", "ALTER TABLE leaves ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'Pending'");
            ensureColumn(conn, "leaves", "applied_at", "ALTER TABLE leaves ADD COLUMN applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");

            ensureColumn(conn, "payslips", "hra", "ALTER TABLE payslips ADD COLUMN hra DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "da", "ALTER TABLE payslips ADD COLUMN da DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "ta", "ALTER TABLE payslips ADD COLUMN ta DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "allowances", "ALTER TABLE payslips ADD COLUMN allowances DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "gross_pay", "ALTER TABLE payslips ADD COLUMN gross_pay DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "pf_deduction", "ALTER TABLE payslips ADD COLUMN pf_deduction DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "professional_tax", "ALTER TABLE payslips ADD COLUMN professional_tax DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "lop_deduction", "ALTER TABLE payslips ADD COLUMN lop_deduction DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "adjustment_earnings", "ALTER TABLE payslips ADD COLUMN adjustment_earnings DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "adjustment_deductions", "ALTER TABLE payslips ADD COLUMN adjustment_deductions DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "deductions", "ALTER TABLE payslips ADD COLUMN deductions DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "total_deductions", "ALTER TABLE payslips ADD COLUMN total_deductions DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "lop_days", "ALTER TABLE payslips ADD COLUMN lop_days DOUBLE NOT NULL DEFAULT 0");
            ensureColumn(conn, "payslips", "working_days", "ALTER TABLE payslips ADD COLUMN working_days INT NOT NULL DEFAULT 30");
            ensureColumn(conn, "payroll_adjustments", "status", "ALTER TABLE payroll_adjustments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'Pending'");
            ensureColumn(conn, "payroll_adjustments", "requested_by_user_id", "ALTER TABLE payroll_adjustments ADD COLUMN requested_by_user_id INT DEFAULT NULL");
            ensureColumn(conn, "payroll_adjustments", "reviewed_by_user_id", "ALTER TABLE payroll_adjustments ADD COLUMN reviewed_by_user_id INT DEFAULT NULL");
            ensureColumn(conn, "payroll_adjustments", "admin_comment", "ALTER TABLE payroll_adjustments ADD COLUMN admin_comment VARCHAR(255) DEFAULT NULL");
            ensureColumn(conn, "payroll_adjustments", "reviewed_at", "ALTER TABLE payroll_adjustments ADD COLUMN reviewed_at TIMESTAMP NULL DEFAULT NULL");

            seedDefaultAdmin(conn);
            backfillLeaveDates(conn);
            backfillPayslipSummaries(conn);

            System.out.println("[DB] Database initialised successfully.");

        } catch (SQLException e) {
            System.out.println("[DB] Initialisation error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String[] authenticateUser(String username, String password) {
        String query = "SELECT id, username, password, password_salt, role FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String storedPassword = rs.getString("password");
                String storedSalt = rs.getString("password_salt");

                if (!passwordMatches(password, storedPassword, storedSalt)) {
                    return null;
                }

                if (storedSalt == null || storedSalt.isBlank()) {
                    upgradeLegacyPassword(conn, rs.getInt("id"), password);
                }

                return new String[]{
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("role")
                };
            }

        } catch (SQLException e) {
            System.out.println("[DB] Login error: " + e.getMessage());
        }

        return null;
    }

    public static boolean addEmployee(String username, String password, String fullName, String dept, String desig, double baseSalary) {
        String insertUser = "INSERT INTO users (username, password, password_salt, role) VALUES (?, ?, ?, 'employee')";
        String insertEmp = "INSERT INTO employees (user_id, full_name, department, designation, base_salary) VALUES (?, ?, ?, ?, ?)";

        if (baseSalary <= 0) {
            return false;
        }

        String salt = generateSalt();
        String passwordHash = hashPassword(password, salt);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement userStmt = conn.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement empStmt = conn.prepareStatement(insertEmp)) {

                userStmt.setString(1, username.trim());
                userStmt.setString(2, passwordHash);
                userStmt.setString(3, salt);
                userStmt.executeUpdate();

                try (ResultSet rs = userStmt.getGeneratedKeys()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    int userId = rs.getInt(1);
                    empStmt.setInt(1, userId);
                    empStmt.setString(2, fullName.trim());
                    empStmt.setString(3, dept.trim());
                    empStmt.setString(4, desig.trim());
                    empStmt.setDouble(5, roundMoney(baseSalary));
                    empStmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLIntegrityConstraintViolationException e) {
                conn.rollback();
                System.out.println("[DB] Add Employee Error: username already exists.");
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("[DB] Add Employee Error: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (SQLException e) {
            System.out.println("[DB] Add Employee Error: " + e.getMessage());
        }

        return false;
    }

    public static JSONArray getAllEmployees() {
        JSONArray list = new JSONArray();
        String query = "SELECT emp_id, full_name, department, designation, base_salary, employment_status, bank_account, email, phone, address, joining_date " +
                "FROM employees ORDER BY full_name";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                JSONObject emp = new JSONObject();
                emp.put("emp_id", rs.getInt("emp_id"));
                emp.put("name", rs.getString("full_name"));
                emp.put("dept", rs.getString("department"));
                emp.put("role", rs.getString("designation"));
                emp.put("salary", roundMoney(rs.getDouble("base_salary")));
                emp.put("status", rs.getString("employment_status"));
                emp.put("policy", getPolicyLabel(rs.getString("designation")));
                emp.put("bankAccount", rs.getString("bank_account"));
                emp.put("email", rs.getString("email"));
                emp.put("phone", rs.getString("phone"));
                emp.put("address", rs.getString("address"));
                emp.put("joiningDate", rs.getString("joining_date"));
                list.put(emp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public static JSONArray getAllAdmins() {
        JSONArray list = new JSONArray();
        String query = "SELECT id, username, role FROM users WHERE role = 'admin' ORDER BY username";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                JSONObject admin = new JSONObject();
                admin.put("id", rs.getInt("id"));
                admin.put("username", rs.getString("username"));
                admin.put("role", rs.getString("role"));
                list.put(admin);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public static boolean addAdmin(String username, String password) {
        String insertAdmin = "INSERT INTO users (username, password, password_salt, role) VALUES (?, ?, ?, 'admin')";

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        String salt = generateSalt();
        String passwordHash = hashPassword(password.trim(), salt);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertAdmin)) {

            stmt.setString(1, username.trim());
            stmt.setString(2, passwordHash);
            stmt.setString(3, salt);
            stmt.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("[DB] Add Admin Error: username already exists.");
        } catch (SQLException e) {
            System.out.println("[DB] Add Admin Error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public static boolean addPayrollAdjustment(int empId, String monthInput, String adjustmentType, double amount, String reason, Integer requestedByUserId) {
        // Adjustments submitted by an admin are auto-approved so they take effect immediately
        // when payroll is next run. The approval queue exists only for subsequent review/revocation.
        String insertAdjustment = "INSERT INTO payroll_adjustments (emp_id, month_year, adjustment_type, amount, reason, requested_by_user_id, status, reviewed_by_user_id, reviewed_at) VALUES (?, ?, ?, ?, ?, ?, 'Approved', ?, CURRENT_TIMESTAMP)";

        if (amount <= 0 || reason == null || reason.isBlank()) {
            return false;
        }

        YearMonth payPeriod;
        try {
            payPeriod = parsePayPeriod(monthInput);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String normalizedType = normalizeAdjustmentType(adjustmentType);
        String monthYear = payPeriod.format(DISPLAY_MONTH_FORMAT);

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(insertAdjustment)) {
                stmt.setInt(1, empId);
                stmt.setString(2, monthYear);
                stmt.setString(3, normalizedType);
                stmt.setDouble(4, roundMoney(amount));
                stmt.setString(5, reason.trim());
                if (requestedByUserId == null) {
                    stmt.setNull(6, java.sql.Types.INTEGER);
                    stmt.setNull(7, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(6, requestedByUserId);
                    stmt.setInt(7, requestedByUserId);
                }
                stmt.executeUpdate();
            }
            // Immediately patch the payslip for this month if it already exists
            patchPayslipTotals(conn, empId, monthYear);
            conn.commit();
            return true;
        } catch (SQLException e) {
            System.out.println("[DB] Add Adjustment Error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public static JSONArray getPayrollAdjustments() {
        JSONArray list = new JSONArray();
        String query = "SELECT a.adjustment_id, a.month_year, a.adjustment_type, a.amount, a.reason, a.status, a.admin_comment, " +
                "e.emp_id, e.full_name, requester.username AS requested_by, reviewer.username AS reviewed_by " +
                "FROM payroll_adjustments a " +
                "JOIN employees e ON a.emp_id = e.emp_id " +
                "LEFT JOIN users requester ON a.requested_by_user_id = requester.id " +
                "LEFT JOIN users reviewer ON a.reviewed_by_user_id = reviewer.id " +
                "ORDER BY a.created_at DESC, a.adjustment_id DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                JSONObject adjustment = new JSONObject();
                adjustment.put("adjustmentId", rs.getInt("adjustment_id"));
                adjustment.put("monthYear", rs.getString("month_year"));
                adjustment.put("type", rs.getString("adjustment_type"));
                adjustment.put("amount", roundMoney(rs.getDouble("amount")));
                adjustment.put("reason", rs.getString("reason"));
                adjustment.put("empId", rs.getInt("emp_id"));
                adjustment.put("employeeName", rs.getString("full_name"));
                adjustment.put("status", rs.getString("status"));
                adjustment.put("adminComment", rs.getString("admin_comment"));
                adjustment.put("requestedBy", rs.getString("requested_by"));
                adjustment.put("reviewedBy", rs.getString("reviewed_by"));
                list.put(adjustment);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public static JSONArray getPendingPayrollAdjustments() {
        JSONArray list = new JSONArray();
        String query = "SELECT a.adjustment_id, a.month_year, a.adjustment_type, a.amount, a.reason, " +
                "e.emp_id, e.full_name, requester.username AS requested_by " +
                "FROM payroll_adjustments a " +
                "JOIN employees e ON a.emp_id = e.emp_id " +
                "LEFT JOIN users requester ON a.requested_by_user_id = requester.id " +
                "WHERE a.status = 'Pending' ORDER BY a.created_at ASC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                JSONObject adjustment = new JSONObject();
                adjustment.put("adjustmentId", rs.getInt("adjustment_id"));
                adjustment.put("monthYear", rs.getString("month_year"));
                adjustment.put("type", rs.getString("adjustment_type"));
                adjustment.put("amount", roundMoney(rs.getDouble("amount")));
                adjustment.put("reason", rs.getString("reason"));
                adjustment.put("empId", rs.getInt("emp_id"));
                adjustment.put("employeeName", rs.getString("full_name"));
                adjustment.put("requestedBy", rs.getString("requested_by"));
                list.put(adjustment);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public static boolean reviewPayrollAdjustment(int adjustmentId, String status, String adminComment, int reviewerUserId) {
        String normalizedStatus = "Approve".equalsIgnoreCase(status) || "Approved".equalsIgnoreCase(status) ? "Approved" : "Rejected";
        String updateAdj = "UPDATE payroll_adjustments SET status = ?, admin_comment = ?, reviewed_by_user_id = ?, reviewed_at = CURRENT_TIMESTAMP WHERE adjustment_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(updateAdj)) {
                stmt.setString(1, normalizedStatus);
                stmt.setString(2, adminComment == null ? "" : adminComment.trim());
                stmt.setInt(3, reviewerUserId);
                stmt.setInt(4, adjustmentId);
                stmt.executeUpdate();
            }
            if ("Approved".equals(normalizedStatus)) {
                patchPayslipForAdjustment(conn, adjustmentId);
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void patchPayslipForAdjustment(Connection conn, int adjustmentId) throws SQLException {
        String fetchAdj = "SELECT emp_id, month_year FROM payroll_adjustments WHERE adjustment_id = ?";
        int empId;
        String monthYear;
        try (PreparedStatement ps = conn.prepareStatement(fetchAdj)) {
            ps.setInt(1, adjustmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return;
                empId = rs.getInt("emp_id");
                monthYear = rs.getString("month_year");
            }
        }
        patchPayslipTotals(conn, empId, monthYear);
    }

    public static JSONArray getPayrollHistory() {
        JSONArray list = new JSONArray();
        String query = "SELECT month_year, COUNT(*) AS employee_count, " +
                "SUM(gross_pay) AS total_gross, SUM(total_deductions) AS total_deductions, " +
                "SUM(net_salary) AS total_net, MAX(generated_date) AS last_run " +
                "FROM payslips GROUP BY month_year ORDER BY MAX(generated_date) DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("monthYear", rs.getString("month_year"));
                item.put("employeeCount", rs.getInt("employee_count"));
                item.put("totalGross", roundMoney(rs.getDouble("total_gross")));
                item.put("totalDeductions", roundMoney(rs.getDouble("total_deductions")));
                item.put("totalNet", roundMoney(rs.getDouble("total_net")));
                item.put("lastRun", String.valueOf(rs.getTimestamp("last_run")));
                list.put(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    public static JSONObject getDepartmentAnalytics(String monthInput) {
        JSONObject result = new JSONObject();
        JSONArray departments = new JSONArray();

        String filterMonth;
        if (monthInput == null || monthInput.isBlank()) {
            filterMonth = null;
        } else {
            filterMonth = parsePayPeriod(monthInput).format(DISPLAY_MONTH_FORMAT);
        }

        String query = filterMonth == null
                ? "SELECT e.department, COUNT(DISTINCT e.emp_id) AS employees, " +
                  "COALESCE(SUM(p.gross_pay), 0) AS gross_total, COALESCE(SUM(p.total_deductions), 0) AS deduction_total, COALESCE(SUM(p.net_salary), 0) AS net_total " +
                  "FROM employees e LEFT JOIN payslips p ON e.emp_id = p.emp_id " +
                  "GROUP BY e.department ORDER BY net_total DESC"
                : "SELECT e.department, COUNT(DISTINCT e.emp_id) AS employees, " +
                  "COALESCE(SUM(p.gross_pay), 0) AS gross_total, COALESCE(SUM(p.total_deductions), 0) AS deduction_total, COALESCE(SUM(p.net_salary), 0) AS net_total " +
                  "FROM employees e LEFT JOIN payslips p ON e.emp_id = p.emp_id AND p.month_year = ? " +
                  "GROUP BY e.department ORDER BY net_total DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            if (filterMonth != null) {
                stmt.setString(1, filterMonth);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();
                    item.put("department", rs.getString("department"));
                    item.put("employees", rs.getInt("employees"));
                    item.put("grossTotal", roundMoney(rs.getDouble("gross_total")));
                    item.put("deductionTotal", roundMoney(rs.getDouble("deduction_total")));
                    item.put("netTotal", roundMoney(rs.getDouble("net_total")));
                    departments.put(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        result.put("monthYear", filterMonth == null ? "All Periods" : filterMonth);
        result.put("departments", departments);
        return result;
    }

    public static JSONObject getEmployeeProfileByUserId(int userId) {
        JSONObject profile = new JSONObject();
        String query = "SELECT e.emp_id, e.user_id, e.full_name, e.department, e.designation, e.base_salary, e.employment_status, " +
                "e.bank_account, e.email, e.phone, e.address, e.joining_date, u.username " +
                "FROM employees e JOIN users u ON e.user_id = u.id WHERE e.user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    profile.put("empId", rs.getInt("emp_id"));
                    profile.put("userId", rs.getInt("user_id"));
                    profile.put("username", rs.getString("username"));
                    profile.put("fullName", rs.getString("full_name"));
                    profile.put("department", rs.getString("department"));
                    profile.put("designation", rs.getString("designation"));
                    profile.put("baseSalary", roundMoney(rs.getDouble("base_salary")));
                    profile.put("status", rs.getString("employment_status"));
                    profile.put("bankAccount", rs.getString("bank_account"));
                    profile.put("email", rs.getString("email"));
                    profile.put("phone", rs.getString("phone"));
                    profile.put("address", rs.getString("address"));
                    profile.put("joiningDate", rs.getString("joining_date"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return profile;
    }

    public static JSONObject getEmployeeProfileByEmpId(int empId) {
        JSONObject profile = new JSONObject();
        String query = "SELECT e.emp_id, e.user_id, e.full_name, e.department, e.designation, e.base_salary, e.employment_status, " +
                "e.bank_account, e.email, e.phone, e.address, e.joining_date, u.username " +
                "FROM employees e JOIN users u ON e.user_id = u.id WHERE e.emp_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, empId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    profile.put("empId", rs.getInt("emp_id"));
                    profile.put("userId", rs.getInt("user_id"));
                    profile.put("username", rs.getString("username"));
                    profile.put("fullName", rs.getString("full_name"));
                    profile.put("department", rs.getString("department"));
                    profile.put("designation", rs.getString("designation"));
                    profile.put("baseSalary", roundMoney(rs.getDouble("base_salary")));
                    profile.put("status", rs.getString("employment_status"));
                    profile.put("bankAccount", rs.getString("bank_account"));
                    profile.put("email", rs.getString("email"));
                    profile.put("phone", rs.getString("phone"));
                    profile.put("address", rs.getString("address"));
                    profile.put("joiningDate", rs.getString("joining_date"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return profile;
    }

    public static JSONArray getEmployeeRevisionHistory(int empId) {
        JSONArray list = new JSONArray();
        String query = "SELECT h.change_summary, h.previous_data, h.new_data, h.created_at, u.username AS changed_by " +
                "FROM employee_revision_history h " +
                "JOIN users u ON h.changed_by_user_id = u.id " +
                "WHERE h.emp_id = ? ORDER BY h.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, empId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();
                    item.put("changeSummary", rs.getString("change_summary"));
                    item.put("previousData", new JSONObject(rs.getString("previous_data")));
                    item.put("newData", new JSONObject(rs.getString("new_data")));
                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                    item.put("changedBy", rs.getString("changed_by"));
                    list.put(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean updateEmployeeProfile(int empId, JSONObject updates, int changedByUserId) {
        String selectQuery = "SELECT full_name, department, designation, base_salary, employment_status, bank_account, email, phone, address, joining_date FROM employees WHERE emp_id = ?";
        String updateQuery = "UPDATE employees SET full_name = ?, department = ?, designation = ?, base_salary = ?, employment_status = ?, bank_account = ?, email = ?, phone = ?, address = ?, joining_date = ? WHERE emp_id = ?";
        String historyQuery = "INSERT INTO employee_revision_history (emp_id, changed_by_user_id, change_summary, previous_data, new_data) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
                selectStmt.setInt(1, empId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    JSONObject previous = new JSONObject();
                    previous.put("fullName", rs.getString("full_name"));
                    previous.put("department", rs.getString("department"));
                    previous.put("designation", rs.getString("designation"));
                    previous.put("baseSalary", roundMoney(rs.getDouble("base_salary")));
                    previous.put("status", rs.getString("employment_status"));
                    previous.put("bankAccount", rs.getString("bank_account"));
                    previous.put("email", rs.getString("email"));
                    previous.put("phone", rs.getString("phone"));
                    previous.put("address", rs.getString("address"));
                    previous.put("joiningDate", rs.getString("joining_date"));

                    JSONObject next = new JSONObject(previous.toString());
                    for (String key : updates.keySet()) {
                        next.put(key, updates.get(key));
                    }

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery);
                         PreparedStatement historyStmt = conn.prepareStatement(historyQuery)) {

                        updateStmt.setString(1, next.optString("fullName", previous.getString("fullName")));
                        updateStmt.setString(2, next.optString("department", previous.getString("department")));
                        updateStmt.setString(3, next.optString("designation", previous.getString("designation")));
                        updateStmt.setDouble(4, roundMoney(next.optDouble("baseSalary", previous.getDouble("baseSalary"))));
                        updateStmt.setString(5, next.optString("status", previous.getString("status")));
                        updateStmt.setString(6, next.optString("bankAccount", previous.getString("bankAccount")));
                        updateStmt.setString(7, next.optString("email", previous.getString("email")));
                        updateStmt.setString(8, next.optString("phone", previous.getString("phone")));
                        updateStmt.setString(9, next.optString("address", previous.getString("address")));
                        String joiningDate = next.optString("joiningDate", previous.optString("joiningDate", ""));
                        if (joiningDate == null || joiningDate.isBlank() || "null".equalsIgnoreCase(joiningDate)) {
                            updateStmt.setNull(10, java.sql.Types.DATE);
                        } else {
                            updateStmt.setDate(10, Date.valueOf(joiningDate));
                        }
                        updateStmt.setInt(11, empId);
                        updateStmt.executeUpdate();

                        historyStmt.setInt(1, empId);
                        historyStmt.setInt(2, changedByUserId);
                        historyStmt.setString(3, "Employee profile updated");
                        historyStmt.setString(4, previous.toString());
                        historyStmt.setString(5, next.toString());
                        historyStmt.executeUpdate();
                    }
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static JSONArray getAllComplaints() {
        JSONArray list = new JSONArray();
        String query = "SELECT c.complaint_id, c.month_year, c.subject, c.message, c.status, c.admin_reply, c.created_at, " +
                "e.emp_id, e.full_name, replier.username AS replied_by " +
                "FROM payroll_complaints c " +
                "JOIN employees e ON c.emp_id = e.emp_id " +
                "LEFT JOIN users replier ON c.replied_by_user_id = replier.id " +
                "ORDER BY c.created_at DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("complaintId", rs.getInt("complaint_id"));
                item.put("monthYear", rs.getString("month_year"));
                item.put("subject", rs.getString("subject"));
                item.put("message", rs.getString("message"));
                item.put("status", rs.getString("status"));
                item.put("adminReply", rs.getString("admin_reply"));
                item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                item.put("employeeName", rs.getString("full_name"));
                item.put("empId", rs.getInt("emp_id"));
                item.put("repliedBy", rs.getString("replied_by"));
                list.put(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static JSONArray getComplaintsForUser(int userId) {
        JSONArray list = new JSONArray();
        String query = "SELECT c.complaint_id, c.month_year, c.subject, c.message, c.status, c.admin_reply, c.created_at " +
                "FROM payroll_complaints c JOIN employees e ON c.emp_id = e.emp_id WHERE e.user_id = ? ORDER BY c.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();
                    item.put("complaintId", rs.getInt("complaint_id"));
                    item.put("monthYear", rs.getString("month_year"));
                    item.put("subject", rs.getString("subject"));
                    item.put("message", rs.getString("message"));
                    item.put("status", rs.getString("status"));
                    item.put("adminReply", rs.getString("admin_reply"));
                    item.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                    list.put(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean createComplaint(int userId, String monthYear, String subject, String message) {
        String empQuery = "SELECT emp_id FROM employees WHERE user_id = ?";
        String insert = "INSERT INTO payroll_complaints (emp_id, month_year, subject, message) VALUES (?, ?, ?, ?)";

        if (subject == null || subject.isBlank() || message == null || message.isBlank()) {
            return false;
        }

        String normalizedMonth = null;
        if (monthYear != null && !monthYear.isBlank()) {
            normalizedMonth = parsePayPeriod(monthYear).format(DISPLAY_MONTH_FORMAT);
        }

        try (Connection conn = getConnection();
             PreparedStatement empStmt = conn.prepareStatement(empQuery)) {
            empStmt.setInt(1, userId);
            try (ResultSet rs = empStmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }

                try (PreparedStatement insertStmt = conn.prepareStatement(insert)) {
                    insertStmt.setInt(1, rs.getInt("emp_id"));
                    if (normalizedMonth == null) {
                        insertStmt.setNull(2, java.sql.Types.VARCHAR);
                    } else {
                        insertStmt.setString(2, normalizedMonth);
                    }
                    insertStmt.setString(3, subject.trim());
                    insertStmt.setString(4, message.trim());
                    return insertStmt.executeUpdate() > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean replyToComplaint(int complaintId, String reply, int adminUserId) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        String query = "UPDATE payroll_complaints SET admin_reply = ?, status = 'Resolved', replied_by_user_id = ?, replied_at = CURRENT_TIMESTAMP WHERE complaint_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, reply.trim());
            stmt.setInt(2, adminUserId);
            stmt.setInt(3, complaintId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String exportPayrollCsv(String monthInput) {
        String filterMonth = normalizeOptionalPayPeriod(monthInput);
        String query = "SELECT p.month_year, e.emp_id, e.full_name, e.department, e.designation, " +
                "p.base_pay, p.hra, p.da, p.ta, p.adjustment_earnings, p.gross_pay, p.pf_deduction, " +
                "p.professional_tax, p.lop_deduction, p.adjustment_deductions, p.total_deductions, p.net_salary " +
                "FROM payslips p JOIN employees e ON p.emp_id = e.emp_id " +
                (filterMonth == null ? "" : "WHERE p.month_year = ? ") +
                "ORDER BY p.month_year DESC, e.department, e.full_name";

        StringBuilder csv = csvBuilder(
                "Month", "Employee ID", "Employee Name", "Department", "Designation",
                "Base Pay", "HRA", "DA", "Transport", "Adjustment Earnings", "Gross Pay",
                "PF", "Professional Tax", "LOP Deduction", "Adjustment Deductions", "Total Deductions", "Net Salary"
        );

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            if (filterMonth != null) {
                stmt.setString(1, filterMonth);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    appendCsvRow(csv,
                            rs.getString("month_year"),
                            rs.getInt("emp_id"),
                            rs.getString("full_name"),
                            rs.getString("department"),
                            rs.getString("designation"),
                            roundMoney(rs.getDouble("base_pay")),
                            roundMoney(rs.getDouble("hra")),
                            roundMoney(rs.getDouble("da")),
                            roundMoney(rs.getDouble("ta")),
                            roundMoney(rs.getDouble("adjustment_earnings")),
                            roundMoney(rs.getDouble("gross_pay")),
                            roundMoney(rs.getDouble("pf_deduction")),
                            roundMoney(rs.getDouble("professional_tax")),
                            roundMoney(rs.getDouble("lop_deduction")),
                            roundMoney(rs.getDouble("adjustment_deductions")),
                            roundMoney(rs.getDouble("total_deductions")),
                            roundMoney(rs.getDouble("net_salary"))
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return csv.toString();
    }

    public static String exportEmployeeMasterCsv() {
        StringBuilder csv = csvBuilder(
                "Employee ID", "Full Name", "Username", "Department", "Designation", "Base Salary",
                "Status", "Bank Account", "Email", "Phone", "Address", "Joining Date"
        );

        String query = "SELECT e.emp_id, e.full_name, u.username, e.department, e.designation, e.base_salary, " +
                "e.employment_status, e.bank_account, e.email, e.phone, e.address, e.joining_date " +
                "FROM employees e JOIN users u ON e.user_id = u.id ORDER BY e.department, e.full_name";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                appendCsvRow(csv,
                        rs.getInt("emp_id"),
                        rs.getString("full_name"),
                        rs.getString("username"),
                        rs.getString("department"),
                        rs.getString("designation"),
                        roundMoney(rs.getDouble("base_salary")),
                        rs.getString("employment_status"),
                        rs.getString("bank_account"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("address"),
                        rs.getString("joining_date")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return csv.toString();
    }

    public static String exportMonthlySummaryCsv() {
        StringBuilder csv = csvBuilder("Month", "Employees", "Gross", "Deductions", "Net");
        for (JSONObject row : getPayrollHistoryRows()) {
            appendCsvRow(csv,
                    row.getString("monthYear"),
                    row.getInt("employeeCount"),
                    row.getDouble("totalGross"),
                    row.getDouble("totalDeductions"),
                    row.getDouble("totalNet")
            );
        }
        return csv.toString();
    }

    public static String exportQuarterlySummaryCsv() {
        StringBuilder csv = csvBuilder("Quarter", "Months Included", "Employees", "Gross", "Deductions", "Net");
        for (JSONObject row : buildQuarterlySummaryRows()) {
            appendCsvRow(csv,
                    row.getString("quarter"),
                    row.getString("months"),
                    row.getInt("employeeCount"),
                    row.getDouble("totalGross"),
                    row.getDouble("totalDeductions"),
                    row.getDouble("totalNet")
            );
        }
        return csv.toString();
    }

    public static String exportYearlySummaryCsv() {
        StringBuilder csv = csvBuilder("Year", "Months Included", "Employees", "Gross", "Deductions", "Net");
        for (JSONObject row : buildYearlySummaryRows()) {
            appendCsvRow(csv,
                    row.getString("year"),
                    row.getString("months"),
                    row.getInt("employeeCount"),
                    row.getDouble("totalGross"),
                    row.getDouble("totalDeductions"),
                    row.getDouble("totalNet")
            );
        }
        return csv.toString();
    }
/* ... (Keep existing imports and methods) ... */

    // ── NEW: DELETE EMPLOYEE ──
    public static boolean deleteEmployee(int empId) {
        String getUserIdQuery = "SELECT user_id FROM employees WHERE emp_id = ?";
        String deleteHistoryQuery = "DELETE FROM employee_revision_history WHERE emp_id = ?";
        String deleteAdjustmentsQuery = "DELETE FROM payroll_adjustments WHERE emp_id = ?";
        String deleteComplaintsQuery = "DELETE FROM payroll_complaints WHERE emp_id = ?";
        String deletePayslipsQuery = "DELETE FROM payslips WHERE emp_id = ?";
        String deleteLeavesQuery = "DELETE FROM leaves WHERE emp_id = ?";
        String deleteEmployeeQuery = "DELETE FROM employees WHERE emp_id = ?";
        String deleteUserQuery = "DELETE FROM users WHERE id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            int userId = -1;

            // 1. Get associated user_id
            try (PreparedStatement ps = conn.prepareStatement(getUserIdQuery)) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) userId = rs.getInt("user_id");
                }
            }

            if (userId == -1) {
                conn.rollback();
                return false;
            }

            // 2. Delete all related records to satisfy foreign key constraints
            try (PreparedStatement psHist = conn.prepareStatement(deleteHistoryQuery);
                 PreparedStatement psAdj = conn.prepareStatement(deleteAdjustmentsQuery);
                 PreparedStatement psComp = conn.prepareStatement(deleteComplaintsQuery);
                 PreparedStatement psSlip = conn.prepareStatement(deletePayslipsQuery);
                 PreparedStatement psLeave = conn.prepareStatement(deleteLeavesQuery);
                 PreparedStatement psEmp = conn.prepareStatement(deleteEmployeeQuery);
                 PreparedStatement psUser = conn.prepareStatement(deleteUserQuery)) {

                psHist.setInt(1, empId); psHist.executeUpdate();
                psAdj.setInt(1, empId);  psAdj.executeUpdate();
                psComp.setInt(1, empId); psComp.executeUpdate();
                psSlip.setInt(1, empId); psSlip.executeUpdate();
                psLeave.setInt(1, empId); psLeave.executeUpdate();
                
                // 3. Delete Employee and finally the User
                psEmp.setInt(1, empId);   psEmp.executeUpdate();
                psUser.setInt(1, userId); psUser.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static JSONObject runPayroll(String monthInput) {
        JSONObject result = new JSONObject();
        YearMonth payPeriod;

        try {
            payPeriod = parsePayPeriod(monthInput);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        }

        String monthYear = payPeriod.format(DISPLAY_MONTH_FORMAT);
        String employeeQuery = "SELECT emp_id, designation, base_salary FROM employees WHERE employment_status = 'Active'";
        String deleteExisting = "DELETE FROM payslips WHERE emp_id = ? AND month_year = ?";
        String insertSlip = "INSERT INTO payslips (" +
                "emp_id, month_year, base_pay, hra, da, ta, allowances, gross_pay, " +
                "pf_deduction, professional_tax, lop_deduction, adjustment_earnings, adjustment_deductions, deductions, total_deductions, " +
                "lop_days, working_days, net_salary" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int generated = 0;
        double totalGross = 0;
        double totalDeductions = 0;
        double totalNet = 0;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement empStmt = conn.prepareStatement(employeeQuery);
                 ResultSet rs = empStmt.executeQuery();
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteExisting);
                 PreparedStatement slipStmt = conn.prepareStatement(insertSlip)) {

                while (rs.next()) {
                    PayrollBreakdown payroll = calculatePayroll(
                            conn,
                            rs.getInt("emp_id"),
                            rs.getString("designation"),
                            rs.getDouble("base_salary"),
                            payPeriod
                    );

                    deleteStmt.setInt(1, rs.getInt("emp_id"));
                    deleteStmt.setString(2, monthYear);
                    deleteStmt.executeUpdate();

                    slipStmt.setInt(1, rs.getInt("emp_id"));
                    slipStmt.setString(2, monthYear);
                    slipStmt.setDouble(3, payroll.basePay);
                    slipStmt.setDouble(4, payroll.hra);
                    slipStmt.setDouble(5, payroll.da);
                    slipStmt.setDouble(6, payroll.ta);
                    slipStmt.setDouble(7, payroll.allowances);
                    slipStmt.setDouble(8, payroll.grossPay);
                    slipStmt.setDouble(9, payroll.pfDeduction);
                    slipStmt.setDouble(10, payroll.professionalTax);
                    slipStmt.setDouble(11, payroll.lopDeduction);
                    slipStmt.setDouble(12, payroll.adjustmentEarnings);
                    slipStmt.setDouble(13, payroll.adjustmentDeductions);
                    slipStmt.setDouble(14, payroll.totalDeductions);
                    slipStmt.setDouble(15, payroll.totalDeductions);
                    slipStmt.setDouble(16, payroll.lopDays);
                    slipStmt.setInt(17, payroll.workingDays);
                    slipStmt.setDouble(18, payroll.netSalary);
                    slipStmt.executeUpdate();

                    generated++;
                    totalGross += payroll.grossPay;
                    totalDeductions += payroll.totalDeductions;
                    totalNet += payroll.netSalary;
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "Payroll run failed because the database operation could not complete.");
            return result;
        }

        result.put("success", true);
        result.put("monthYear", monthYear);
        result.put("generated", generated);
        result.put("totalGross", roundMoney(totalGross));
        result.put("totalDeductions", roundMoney(totalDeductions));
        result.put("totalNet", roundMoney(totalNet));
        return result;
    }

    public static JSONArray getPayslipsForUser(int userId) {
        JSONArray list = new JSONArray();
        String query = "SELECT p.*, e.full_name, e.designation " +
                "FROM payslips p " +
                "JOIN employees e ON p.emp_id = e.emp_id " +
                "WHERE e.user_id = ? " +
                "ORDER BY p.generated_date DESC, p.slip_id DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double basePay = roundMoney(rs.getDouble("base_pay"));
                    double allowances = roundMoney(rs.getDouble("allowances"));
                    double deductions = roundMoney(readDeductions(rs));
                    double grossPay = roundMoney(readGrossPay(rs, basePay, allowances));

                    JSONObject slip = new JSONObject();
                    slip.put("month", rs.getString("month_year"));
                    slip.put("employeeName", rs.getString("full_name"));
                    slip.put("designation", rs.getString("designation"));
                    slip.put("base", basePay);
                    slip.put("hra", roundMoney(rs.getDouble("hra")));
                    slip.put("da", roundMoney(rs.getDouble("da")));
                    slip.put("ta", roundMoney(rs.getDouble("ta")));
                    slip.put("adjustmentEarnings", roundMoney(rs.getDouble("adjustment_earnings")));
                    slip.put("adjustmentDeductions", roundMoney(rs.getDouble("adjustment_deductions")));
                    slip.put("allowances", allowances);
                    slip.put("gross", grossPay);
                    slip.put("pf", roundMoney(rs.getDouble("pf_deduction")));
                    slip.put("pt", roundMoney(rs.getDouble("professional_tax")));
                    slip.put("lop", roundMoney(rs.getDouble("lop_deduction")));
                    slip.put("lopDays", roundMoney(rs.getDouble("lop_days")));
                    slip.put("workingDays", rs.getInt("working_days"));
                    slip.put("deductions", deductions);
                    slip.put("net", roundMoney(rs.getDouble("net_salary")));
                    list.put(slip);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

private static PayrollBreakdown calculatePayroll(Connection conn, int empId, String designation, double baseSalary, YearMonth payPeriod) throws SQLException {
    PayrollPolicy policy = getPolicyForDesignation(designation);
    int workingDays = payPeriod.lengthOfMonth();
    
    // FETCH ADJUSTMENTS (The missing part!)
    AdjustmentTotals adjustmentTotals = getAdjustmentTotals(conn, empId, payPeriod); 

    double basePay = roundMoney(baseSalary);
    double hra = roundMoney(basePay * policy.hraRate);
    double da = roundMoney(basePay * policy.daRate);
    double ta = roundMoney(policy.transportAllowance);

    // allowances = standard components only (HRA + DA + TA), NOT bonuses.
    // Bonuses/adjustment earnings are shown as a separate line on the payslip.
    double allowances = roundMoney(hra + da + ta);
    double grossPay = roundMoney(basePay + allowances);

    double pfDeduction = roundMoney(basePay * 0.12);
    double professionalTax = roundMoney(getProfessionalTax(grossPay));

    double lopDays = getLossOfPayDays(conn, empId, payPeriod);
    double lopDeduction = roundMoney((grossPay / workingDays) * lopDays);

    // Total deductions include standard deductions + any adjustment deductions
    double totalDeductions = roundMoney(pfDeduction + professionalTax + lopDeduction + adjustmentTotals.deductions);

    // Net = gross + bonus earnings - total deductions
    double netSalary = roundMoney(Math.max(0, grossPay + adjustmentTotals.earnings - totalDeductions));

    return new PayrollBreakdown(
        basePay, hra, da, ta, adjustmentTotals.earnings,
        adjustmentTotals.deductions, allowances, grossPay,
        pfDeduction, professionalTax, lopDeduction,
        totalDeductions, lopDays, workingDays, netSalary
    );
}

    private static double getLossOfPayDays(Connection conn, int empId, YearMonth payPeriod) throws SQLException {
        String query = "SELECT COALESCE(SUM(days_count), 0) AS lop_days " +
                "FROM leaves " +
                "WHERE emp_id = ? " +
                "AND start_date <= ? " +
                "AND end_date >= ? " +
                "AND (status = 'Rejected' OR status = 'Absent' OR (status = 'Approved' AND leave_type = 'UNPAID'))";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, empId);
            stmt.setDate(2, Date.valueOf(payPeriod.atEndOfMonth()));
            stmt.setDate(3, Date.valueOf(payPeriod.atDay(1)));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return roundMoney(rs.getDouble("lop_days"));
                }
            }
        }

        return 0;
    }

    /**
     * Recalculates adjustment totals from the payroll_adjustments table and patches
     * the existing payslip row for this employee+month in-place.
     * Called after an adjustment is approved so the employee sees the update immediately.
     */
    private static void patchPayslipTotals(Connection conn, int empId, String monthYear) throws SQLException {
        // Check a payslip exists for this employee/month
        String checkSlip = "SELECT slip_id, gross_pay, pf_deduction, professional_tax, lop_deduction FROM payslips WHERE emp_id = ? AND month_year = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSlip)) {
            ps.setInt(1, empId);
            ps.setString(2, monthYear);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return; // no payslip yet — will be picked up on next payroll run

                double grossPay        = rs.getDouble("gross_pay");
                double pfDeduction     = rs.getDouble("pf_deduction");
                double professionalTax = rs.getDouble("professional_tax");
                double lopDeduction    = rs.getDouble("lop_deduction");

                // Sum all approved adjustments for this employee+month
                String sumAdj = "SELECT " +
                    "COALESCE(SUM(CASE WHEN adjustment_type = 'EARNING' THEN amount ELSE 0 END), 0) AS earnings, " +
                    "COALESCE(SUM(CASE WHEN adjustment_type = 'DEDUCTION' THEN amount ELSE 0 END), 0) AS deductions " +
                    "FROM payroll_adjustments WHERE emp_id = ? AND month_year = ? AND status = 'Approved'";

                double totalEarnings = 0;
                double totalDeductions = 0;
                try (PreparedStatement ps2 = conn.prepareStatement(sumAdj)) {
                    ps2.setInt(1, empId);
                    ps2.setString(2, monthYear);
                    try (ResultSet rs2 = ps2.executeQuery()) {
                        if (rs2.next()) {
                            totalEarnings   = roundMoney(rs2.getDouble("earnings"));
                            totalDeductions = roundMoney(rs2.getDouble("deductions"));
                        }
                    }
                }

                double newTotalDeductions = roundMoney(pfDeduction + professionalTax + lopDeduction + totalDeductions);
                double newNetSalary = roundMoney(Math.max(0, grossPay + totalEarnings - newTotalDeductions));

                String updateSlip = "UPDATE payslips SET " +
                    "adjustment_earnings = ?, " +
                    "adjustment_deductions = ?, " +
                    "total_deductions = ?, " +
                    "deductions = ?, " +
                    "net_salary = ? " +
                    "WHERE emp_id = ? AND month_year = ?";

                try (PreparedStatement ps3 = conn.prepareStatement(updateSlip)) {
                    ps3.setDouble(1, totalEarnings);
                    ps3.setDouble(2, totalDeductions);
                    ps3.setDouble(3, newTotalDeductions);
                    ps3.setDouble(4, newTotalDeductions);
                    ps3.setDouble(5, newNetSalary);
                    ps3.setInt(6, empId);
                    ps3.setString(7, monthYear);
                    ps3.executeUpdate();
                }
            }
        }
    }

    private static AdjustmentTotals getAdjustmentTotals(Connection conn, int empId, YearMonth payPeriod) throws SQLException {
    // This ensures we look for "May 2026" specifically, matching our display format
    String formattedPeriod = payPeriod.format(DISPLAY_MONTH_FORMAT); 
    
    String query = "SELECT " +
            "COALESCE(SUM(CASE WHEN adjustment_type = 'EARNING' THEN amount ELSE 0 END), 0) AS earnings, " +
            "COALESCE(SUM(CASE WHEN adjustment_type = 'DEDUCTION' THEN amount ELSE 0 END), 0) AS deductions " +
            "FROM payroll_adjustments WHERE emp_id = ? AND month_year = ? AND status = 'Approved'";

    try (PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.setInt(1, empId);
        stmt.setString(2, formattedPeriod); // Use the formatted string here

        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new AdjustmentTotals(
                        roundMoney(rs.getDouble("earnings")),
                        roundMoney(rs.getDouble("deductions"))
                );
            }
        }
    }
    return new AdjustmentTotals(0, 0);
}
    private static double getProfessionalTax(double grossPay) {
        if (grossPay >= 80_000) return 300;
        if (grossPay >= 50_000) return 200;
        if (grossPay >= 25_000) return 125;
        return 0;
    }

    private static Integer getEmployeeIdForUser(int userId) {
        String query = "SELECT emp_id FROM employees WHERE user_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("emp_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String normalizeOptionalPayPeriod(String monthInput) {
        if (monthInput == null || monthInput.isBlank()) {
            return null;
        }
        return parsePayPeriod(monthInput).format(DISPLAY_MONTH_FORMAT);
    }

    private static List<JSONObject> getPayrollHistoryRows() {
        List<JSONObject> rows = new ArrayList<>();
        String query = "SELECT month_year, COUNT(*) AS employee_count, " +
                "SUM(gross_pay) AS total_gross, SUM(total_deductions) AS total_deductions, " +
                "SUM(net_salary) AS total_net, MAX(generated_date) AS last_run " +
                "FROM payslips GROUP BY month_year ORDER BY MAX(generated_date) DESC";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("monthYear", rs.getString("month_year"));
                item.put("employeeCount", rs.getInt("employee_count"));
                item.put("totalGross", roundMoney(rs.getDouble("total_gross")));
                item.put("totalDeductions", roundMoney(rs.getDouble("total_deductions")));
                item.put("totalNet", roundMoney(rs.getDouble("total_net")));
                item.put("lastRun", String.valueOf(rs.getTimestamp("last_run")));
                rows.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    private static List<JSONObject> buildQuarterlySummaryRows() {
        Map<String, JSONObject> grouped = new LinkedHashMap<>();
        for (JSONObject row : getPayrollHistoryRows()) {
            YearMonth month = parsePayPeriod(row.getString("monthYear"));
            int quarter = ((month.getMonthValue() - 1) / 3) + 1;
            String key = month.getYear() + " Q" + quarter;

            JSONObject bucket = grouped.computeIfAbsent(key, ignored -> {
                JSONObject value = new JSONObject();
                value.put("quarter", key);
                value.put("months", new JSONArray());
                value.put("employeeCount", 0);
                value.put("totalGross", 0.0);
                value.put("totalDeductions", 0.0);
                value.put("totalNet", 0.0);
                return value;
            });

            bucket.getJSONArray("months").put(row.getString("monthYear"));
            bucket.put("employeeCount", bucket.getInt("employeeCount") + row.getInt("employeeCount"));
            bucket.put("totalGross", roundMoney(bucket.getDouble("totalGross") + row.getDouble("totalGross")));
            bucket.put("totalDeductions", roundMoney(bucket.getDouble("totalDeductions") + row.getDouble("totalDeductions")));
            bucket.put("totalNet", roundMoney(bucket.getDouble("totalNet") + row.getDouble("totalNet")));
        }

        List<JSONObject> summary = new ArrayList<>();
        for (JSONObject value : grouped.values()) {
            value.put("months", String.join(" | ", jsonArrayToList(value.getJSONArray("months"))));
            summary.add(value);
        }
        return summary;
    }

    private static List<JSONObject> buildYearlySummaryRows() {
        Map<String, JSONObject> grouped = new LinkedHashMap<>();
        for (JSONObject row : getPayrollHistoryRows()) {
            YearMonth month = parsePayPeriod(row.getString("monthYear"));
            String key = String.valueOf(month.getYear());

            JSONObject bucket = grouped.computeIfAbsent(key, ignored -> {
                JSONObject value = new JSONObject();
                value.put("year", key);
                value.put("months", new JSONArray());
                value.put("employeeCount", 0);
                value.put("totalGross", 0.0);
                value.put("totalDeductions", 0.0);
                value.put("totalNet", 0.0);
                return value;
            });

            bucket.getJSONArray("months").put(row.getString("monthYear"));
            bucket.put("employeeCount", bucket.getInt("employeeCount") + row.getInt("employeeCount"));
            bucket.put("totalGross", roundMoney(bucket.getDouble("totalGross") + row.getDouble("totalGross")));
            bucket.put("totalDeductions", roundMoney(bucket.getDouble("totalDeductions") + row.getDouble("totalDeductions")));
            bucket.put("totalNet", roundMoney(bucket.getDouble("totalNet") + row.getDouble("totalNet")));
        }

        List<JSONObject> summary = new ArrayList<>();
        for (JSONObject value : grouped.values()) {
            value.put("months", String.join(" | ", jsonArrayToList(value.getJSONArray("months"))));
            summary.add(value);
        }
        return summary;
    }

    private static List<String> jsonArrayToList(JSONArray array) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            values.add(array.getString(i));
        }
        return values;
    }

    private static StringBuilder csvBuilder(Object... headers) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, headers);
        return csv;
    }

    private static void appendCsvRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append('"').append(String.valueOf(values[i] == null ? "" : values[i]).replace("\"", "\"\"")).append('"');
        }
        csv.append('\n');
    }

    private static PayrollPolicy getPolicyForDesignation(String designation) {
        String value = designation == null ? "" : designation.toLowerCase(Locale.ENGLISH);

        if (value.contains("manager")) {
            return new PayrollPolicy(0.26, 0.14, 4_500, "Manager Band");
        }
        if (value.contains("lead") || value.contains("senior")) {
            return new PayrollPolicy(0.22, 0.11, 3_500, "Leadership Band");
        }
        if (value.contains("developer") || value.contains("engineer")) {
            return new PayrollPolicy(0.20, 0.10, 2_800, "Technical Band");
        }
        if (value.contains("hr") || value.contains("recruit") || value.contains("account")) {
            return new PayrollPolicy(0.18, 0.08, 2_200, "Operations Band");
        }
        if (value.contains("intern") || value.contains("trainee")) {
            return new PayrollPolicy(0.08, 0.00, 1_000, "Intern Band");
        }

        return new PayrollPolicy(0.16, 0.07, 1_800, "Standard Band");
    }

    private static String getPolicyLabel(String designation) {
        return getPolicyForDesignation(designation).label;
    }

    private static String normalizeAdjustmentType(String adjustmentType) {
        if (adjustmentType == null) {
            return "EARNING";
        }

        String value = adjustmentType.trim().toUpperCase(Locale.ENGLISH);
        if ("DEDUCTION".equals(value) || "PENALTY".equals(value)) {
            return "DEDUCTION";
        }
        return "EARNING";
    }

    private static YearMonth parsePayPeriod(String rawMonth) {
        if (rawMonth == null || rawMonth.isBlank()) {
            throw new IllegalArgumentException("Please enter a payroll period like 'April 2026' or '2026-04'.");
        }

        String trimmed = rawMonth.trim();
        for (DateTimeFormatter formatter : PAYROLL_INPUT_FORMATS) {
            try {
                return YearMonth.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        throw new IllegalArgumentException("Payroll period format not recognised. Use 'April 2026' or '2026-04'.");
    }

    private static String generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private static String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, HASH_ITERATIONS, HASH_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Unable to hash password", e);
        }
    }

    private static boolean passwordMatches(String inputPassword, String storedPassword, String storedSalt) {
        if (storedSalt == null || storedSalt.isBlank()) {
            return storedPassword != null && storedPassword.equals(inputPassword);
        }

        return storedPassword != null && storedPassword.equals(hashPassword(inputPassword, storedSalt));
    }

    private static void upgradeLegacyPassword(Connection conn, int userId, String plainPassword) throws SQLException {
        String salt = generateSalt();
        String hash = hashPassword(plainPassword, salt);
        String update = "UPDATE users SET password = ?, password_salt = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(update)) {
            stmt.setString(1, hash);
            stmt.setString(2, salt);
            stmt.setInt(3, userId);
            stmt.executeUpdate();
        }
    }

    private static void seedDefaultAdmin(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {

            if (!rs.next() || rs.getInt(1) != 0) {
                return;
            }
        }

        String salt = generateSalt();
        String passwordHash = hashPassword("admin123", salt);

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO users (username, password, password_salt, role) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, "admin");
            stmt.setString(2, passwordHash);
            stmt.setString(3, salt);
            stmt.setString(4, "admin");
            stmt.executeUpdate();
        }

        System.out.println("[DB] Default admin user created: admin / admin123");
    }

    private static void ensureColumn(Connection conn, String tableName, String columnName, String ddl) throws SQLException {
        if (hasColumn(conn, tableName, columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(ddl);
        }
    }

    private static boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet rs = meta.getColumns(conn.getCatalog(), null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static void backfillLeaveDates(Connection conn) throws SQLException {
        boolean hasLegacyLeaveDate = hasColumn(conn, "leaves", "leave_date");
        boolean hasStartDate = hasColumn(conn, "leaves", "start_date");
        boolean hasEndDate = hasColumn(conn, "leaves", "end_date");

        if (!hasLegacyLeaveDate || !hasStartDate || !hasEndDate) {
            return;
        }

        String update = "UPDATE leaves " +
                "SET start_date = COALESCE(start_date, STR_TO_DATE(leave_date, '%Y-%m-%d')), " +
                "end_date = COALESCE(end_date, STR_TO_DATE(leave_date, '%Y-%m-%d')) " +
                "WHERE leave_date IS NOT NULL";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(update);
        }
    }

    private static void backfillPayslipSummaries(Connection conn) throws SQLException {
        String update = "UPDATE payslips SET " +
                "allowances = CASE " +
                "WHEN allowances = 0 AND (hra <> 0 OR da <> 0 OR ta <> 0) THEN hra + da + ta " +
                "ELSE allowances END, " +
                "gross_pay = CASE " +
                "WHEN gross_pay = 0 THEN base_pay + allowances " +
                "ELSE gross_pay END, " +
                "adjustment_earnings = CASE WHEN adjustment_earnings < 0 THEN 0 ELSE adjustment_earnings END, " +
                "adjustment_deductions = CASE WHEN adjustment_deductions < 0 THEN 0 ELSE adjustment_deductions END, " +
                "total_deductions = CASE " +
                "WHEN total_deductions = 0 AND deductions <> 0 THEN deductions " +
                "WHEN total_deductions = 0 THEN pf_deduction + professional_tax + lop_deduction + adjustment_deductions " +
                "ELSE total_deductions END, " +
                "deductions = CASE " +
                "WHEN deductions = 0 AND total_deductions <> 0 THEN total_deductions " +
                "WHEN deductions = 0 THEN pf_deduction + professional_tax + lop_deduction + adjustment_deductions " +
                "ELSE deductions END " +
                "WHERE 1 = 1";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(update);
        }
    }

    private static double readGrossPay(ResultSet rs, double basePay, double allowances) throws SQLException {
        double grossPay = rs.getDouble("gross_pay");
        if (grossPay > 0) {
            return grossPay;
        }
        return basePay + allowances;
    }

    private static double readDeductions(ResultSet rs) throws SQLException {
        double totalDeductions = rs.getDouble("total_deductions");
        if (totalDeductions > 0) {
            return totalDeductions;
        }

        double deductions = rs.getDouble("deductions");
        if (deductions > 0) {
            return deductions;
        }

        return rs.getDouble("pf_deduction") + rs.getDouble("professional_tax") + rs.getDouble("lop_deduction");
    }

    private static double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class PayrollPolicy {
        private final double hraRate;
        private final double daRate;
        private final double transportAllowance;
        private final String label;

        private PayrollPolicy(double hraRate, double daRate, double transportAllowance, String label) {
            this.hraRate = hraRate;
            this.daRate = daRate;
            this.transportAllowance = transportAllowance;
            this.label = label;
        }
    }

    private static final class AdjustmentTotals {
        private final double earnings;
        private final double deductions;

        private AdjustmentTotals(double earnings, double deductions) {
            this.earnings = earnings;
            this.deductions = deductions;
        }
    }

    private static final class PayrollBreakdown {
        private final double basePay;
        private final double hra;
        private final double da;
        private final double ta;
        private final double adjustmentEarnings;
        private final double adjustmentDeductions;
        private final double allowances;
        private final double grossPay;
        private final double pfDeduction;
        private final double professionalTax;
        private final double lopDeduction;
        private final double totalDeductions;
        private final double lopDays;
        private final int workingDays;
        private final double netSalary;

        private PayrollBreakdown(
                double basePay,
                double hra,
                double da,
                double ta,
                double adjustmentEarnings,
                double adjustmentDeductions,
                double allowances,
                double grossPay,
                double pfDeduction,
                double professionalTax,
                double lopDeduction,
                double totalDeductions,
                double lopDays,
                int workingDays,
                double netSalary
        ) {
            this.basePay = basePay;
            this.hra = hra;
            this.da = da;
            this.ta = ta;
            this.adjustmentEarnings = adjustmentEarnings;
            this.adjustmentDeductions = adjustmentDeductions;
            this.allowances = allowances;
            this.grossPay = grossPay;
            this.pfDeduction = pfDeduction;
            this.professionalTax = professionalTax;
            this.lopDeduction = lopDeduction;
            this.totalDeductions = totalDeductions;
            this.lopDays = lopDays;
            this.workingDays = workingDays;
            this.netSalary = netSalary;
        }
    }
}
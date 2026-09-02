import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main.java
 * Entry point for the PayCore Payroll System.
 *
 * Responsibilities:
 * 1. Initialise the database (create tables + seed admin).
 * 2. Start Java's built-in HttpServer on port 8000.
 * 3. Map API endpoints to their handlers.
 * 4. Serve static frontend files from src/static/.
 *
 * Run from project root with:
 * javac -cp "lib/*" -d out src/*.java
 * java  -cp "out;lib/*" Main          (Windows)
 * java  -cp "out:lib/*" Main          (Mac / Linux)
 */
public class Main {

    private static final int    PORT         = 8000;
    private static final String STATIC_DIR   = "src/static";

    public static void main(String[] args) throws IOException {

        // ── Step 1: Initialise database tables ───────────────────────────
        System.out.println("[Server] Initialising database...");
        Database.init();

        // ── Step 2: Create HTTP server ────────────────────────────────────
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        System.out.println("[Server] HTTP server created on port " + PORT);

        // ── Step 3: Register API endpoints ───────────────────────────────
        server.createContext("/api/login", new AuthHandler());
        server.createContext("/api/logout", new AuthHandler());
        server.createContext("/api/session", new AuthHandler());
        System.out.println("[Server] Endpoints registered: /api/login, /api/logout, /api/session");

        // ── Phase 3 & Phase 4 Endpoints Wired Up ──
        server.createContext("/api/admin",    new AdminHandler());
        server.createContext("/api/employee", new EmployeeHandler());
        System.out.println("[Server] Endpoints registered: /api/admin & /api/employee");

        // ── Step 4: Serve static frontend files ───────────────────────────
        // Anything not matched by an /api route falls through to here.
        // Maps URL paths to files inside src/static/.
        server.createContext("/", exchange -> {
            String requestedPath = exchange.getRequestURI().getPath();

            // Default to index.html for the root path
            if (requestedPath.equals("/")) {
                requestedPath = "/index.html";
            }

            Path filePath = Paths.get(STATIC_DIR + requestedPath);

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                // ── File found: detect content type and serve it ──
                String contentType = detectContentType(requestedPath);
                byte[] bytes = Files.readAllBytes(filePath);

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                // ── File not found: return 404 ──
                String response = "404 - File Not Found: " + requestedPath;
                byte[] bytes    = response.getBytes();

                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        System.out.println("[Server] Static file server registered: GET /");

        // ── Step 5: Start the server ──────────────────────────────────────
        server.setExecutor(null); // Use the default single-threaded executor
        server.start();

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  PayCore server running → http://localhost:" + PORT);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Returns an appropriate Content-Type header value based on file extension.
     * Keeps the static file server well-behaved for HTML, CSS, JS, and common assets.
     *
     * @param path  The requested URL path (e.g. "/style.css").
     * @return      MIME type string.
     */
    private static String detectContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css"))  return "text/css; charset=UTF-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=UTF-8";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream"; // safe fallback for unknown types
    }
}

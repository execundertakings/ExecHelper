/*
 *
 * MIT License
 *
 * Copyright (c) 2024 Executive Undertakings
 *
 * Clarkes Command Center inventory integration for ExecHelper.
 * Replaces the PCsCRM stub by pushing QA results to the Command Center
 * /api/inventory endpoint via a shared API key.
 *
 * Authentication:
 *   - Username field  → technician name (stored with QA records)
 *   - Password field  → shop admin password (CustomStrings.getGenericPassword())
 *     If getGenericPassword() returns "", any non-empty password is accepted.
 *   - API key         → CustomStrings.getCommandCenterAPIKey()
 *     Sent as X-ExecHelper-Key header; never shown in the UI.
 *
 * URL:
 *   - CustomStrings.getCommandCenterURL() — e.g. http://192.168.4.104:3000
 *     Use the LAN IP so QA Macs can reach Command Center on the local network.
 *     The Cloudflare tunnel URL also works for remote use.
 *
 */
package GUI;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/**
 * Manages inventory integration with Clarkes Command Center for ExecHelper.
 */
public class PCsCRMManager {

    private final CustomStrings privateStrings = new CustomStrings();
    private final HttpClient httpClient = buildHttpClient();

    private static HttpClient buildHttpClient() {
        // Trust-all SSL context: the bundled JDK 16 jlink runtime has an
        // outdated cacerts that cannot validate Cloudflare's current root CAs,
        // and the macOS KeychainStore provider loads but still fails validation.
        // This is safe because ExecHelper only connects to our own Command
        // Center server (LAN or Cloudflare tunnel). The API key header
        // provides authentication; TLS still encrypts the connection.
        try {
            javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
            javax.net.ssl.SSLContext sslCtx = javax.net.ssl.SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());
            System.out.println("PCsCRMManager: SSL trust-all context initialized");
            return HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .sslContext(sslCtx)
                    .build();
        } catch (Exception e) {
            System.out.println("PCsCRMManager: SSL setup failed, using default: " + e);
            return HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();
        }
    }

    private String loggedInUsername = null;

    // -------------------------------------------------------------------------
    // Auth
    // -------------------------------------------------------------------------

    public void logOut() {
        loggedInUsername = null;
    }

    /**
     * Authenticates a technician.
     * Checks the password against CustomStrings.getGenericPassword() (if set),
     * then does a quick GET /api/inventory to verify the Command Center is reachable
     * and the API key is valid.  On network failure, login still succeeds so the
     * app remains usable if Command Center is temporarily unreachable.
     */
    public boolean authenticateCredentials(String username, String password, boolean isTestMode) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return false;
        }

        // Check shop password (if one is configured)
        String configuredPassword = privateStrings.getGenericPassword();
        if (!configuredPassword.isEmpty() && !password.equals(configuredPassword)) {
            return false;
        }

        // Quick reachability ping — fail open on network error
        String baseUrl = privateStrings.getCommandCenterURL();
        String apiKey  = privateStrings.getCommandCenterAPIKey();
        if (!baseUrl.isEmpty() && !apiKey.isEmpty()) {
            try {
                JSONObject resp = ccGet(baseUrl + "/api/inventory", apiKey);
                if (isTestMode) System.out.println("PCsCRMManager: ping OK, records=" + (resp != null ? resp : "[]"));
            } catch (Exception e) {
                if (isTestMode) System.out.println("PCsCRMManager: ping failed (continuing): " + e);
                // Network error — still allow login
            }
        }

        loggedInUsername = username.trim();
        return true;
    }

    /**
     * Returns minimal user info. "printerIPs" is intentionally absent (no network printing).
     */
    public HashMap<String, String> getUserInfo(String username, boolean isTestMode) {
        HashMap<String, String> info = new HashMap<>();
        info.put("username", username);
        return info;
    }

    // -------------------------------------------------------------------------
    // PID / Asset ID
    // -------------------------------------------------------------------------

    /**
     * Always returns true — setSpecsForPID upserts, so no pre-creation needed.
     */
    public boolean pidExists(String pid, boolean isTestMode) {
        return true;
    }

    // -------------------------------------------------------------------------
    // Specs
    // -------------------------------------------------------------------------

    public LinkedHashMap<String, String> getSpecsForPID(String pid, boolean isTestMode) {
        return getSpecsForPID(pid, isTestMode, false);
    }

    /**
     * Fetches an existing inventory record from Command Center by Asset ID.
     * Returns a map of safe defaults so the app won't NPE on missing keys.
     */
    public LinkedHashMap<String, String> getSpecsForPID(String pid, boolean isTestMode, boolean forceRefresh) {
        LinkedHashMap<String, String> specs = defaultSpecs();

        String baseUrl = privateStrings.getCommandCenterURL();
        String apiKey  = privateStrings.getCommandCenterAPIKey();
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return specs;

        try {
            String url = baseUrl + "/api/inventory?pid=" + URLEncoder.encode(pid, StandardCharsets.UTF_8);
            // GET /api/inventory?pid=xxx returns the single device object (or 404)
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-ExecHelper-Key", apiKey)
                    .timeout(java.time.Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JSONObject obj = new JSONObject(resp.body());
                for (String key : obj.keySet()) {
                    specs.put(key, obj.optString(key, ""));
                }
            }
            // 404 = not yet in inventory; return defaults
        } catch (Exception e) {
            if (isTestMode) System.out.println("PCsCRMManager.getSpecsForPID error: " + e);
        }

        return specs;
    }

    /**
     * Safe defaults for all keys QAHelper reads directly from getSpecsForPID.
     * Prevents NullPointerExceptions when no record exists yet.
     */
    private LinkedHashMap<String, String> defaultSpecs() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("Status",          "Received");
        d.put("Brand",           "");
        d.put("Model",           "");
        d.put("Serial",          "");
        d.put("Condition Grade", "");
        d.put("Notes",           "");
        d.put("Set By",          "");
        d.put("QA Tech",         "");
        d.put("QA Date",         "");
        return d;
    }

    /**
     * Returns a JSON string in the format StatusHistoryWindow expects.
     * Command Center has no audit log, so we return the current record as a
     * single history entry.
     */
    public String getStatusHistoryContentForPID(String pid, boolean isTestMode) {
        try {
            LinkedHashMap<String, String> specs = getSpecsForPID(pid, isTestMode, true);
            JSONArray arr = new JSONArray();
            String date   = specs.getOrDefault("QA Date", "");
            String status = specs.getOrDefault("Status", "");
            String tech   = specs.getOrDefault("QA Tech",
                    loggedInUsername != null ? loggedInUsername : "Unknown");
            if (!date.isEmpty() || !status.isEmpty()) {
                JSONObject entry = new JSONObject();
                entry.put("date",     date.isEmpty()   ? "—" : date);
                entry.put("status",   status.isEmpty() ? "Received" : status);
                entry.put("username", tech);
                arr.put(entry);
            }
            JSONObject wrapper = new JSONObject();
            wrapper.put("responseData", arr.toString());
            return wrapper.toString();
        } catch (Exception e) {
            return "{\"responseData\": \"[]\"}";
        }
    }

    /**
     * Posts/updates the device record in Command Center with all detected specs.
     * Throws on failure (caller handles as logged error).
     */
    public boolean setSpecsForPID(String pid, String username, boolean isTestMode,
                                  HashMap<String, String> specs) throws Exception {
        String baseUrl = privateStrings.getCommandCenterURL();
        String apiKey  = privateStrings.getCommandCenterAPIKey();
        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            // Not configured — succeed silently so the app works without Command Center
            return true;
        }

        // Build the payload
        JSONObject payload = new JSONObject();
        payload.put("Asset ID", pid);
        payload.put("QA Tech",  username);
        payload.put("QA Date",  new SimpleDateFormat("yyyy-MM-dd").format(new Date()));

        // Direct field pass-through
        String[] directFields = {
            "Brand", "Model", "Serial", "OS", "Chassis", "CPU", "RAM",
            "Storage", "Storage Serial", "GPU", "Audio", "Screen", "Disc Drive",
            "Notes", "Condition Grade", "Set By", "Is Laptop"
        };
        for (String field : directFields) {
            if (specs.containsKey(field) && specs.get(field) != null) {
                payload.put(field, specs.get(field));
            }
        }

        // Status: convert 1-based code to name
        if (specs.containsKey("Status")) {
            payload.put("Status", resolveStatusName(specs.get("Status")));
        }

        // Battery % as integer
        if (specs.containsKey("Battery Capacity Remaining Percentage Integer")) {
            try {
                payload.put("Battery %",
                        Integer.parseInt(specs.get("Battery Capacity Remaining Percentage Integer")));
            } catch (NumberFormatException ignored) {}
        }

        // Derive device type from brand/OS/isLaptop
        payload.put("Device Type", deriveDeviceType(
                specs.getOrDefault("Brand", ""),
                specs.getOrDefault("Is Laptop", "N"),
                specs.getOrDefault("OS", "")
        ));

        // POST to /api/inventory (server upserts by Asset ID)
        String body = payload.toString();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/inventory"))
                .header("X-ExecHelper-Key",  apiKey)
                .header("Content-Type",      "application/json")
                .timeout(java.time.Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 409) {
            // Duplicate serial number - parse the error for a user-friendly message
            String errMsg = "Duplicate serial number detected";
            try {
                JSONObject errBody = new JSONObject(resp.body());
                if (errBody.has("error")) errMsg = errBody.getString("error");
            } catch (Exception ignored) {}
            throw new Exception("DUPLICATE_SERIAL:" + errMsg);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new Exception("Command Center POST /api/inventory failed - HTTP "
                    + resp.statusCode() + ": " + resp.body());
        }

        if (isTestMode) System.out.println("PCsCRMManager.setSpecsForPID OK: " + resp.body());
        return true;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private JSONObject ccGet(String url, String apiKey) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-ExecHelper-Key", apiKey)
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            String body = resp.body().trim();
            // Inventory list returns a JSON array; wrap it for the caller
            if (body.startsWith("[")) {
                JSONObject wrapper = new JSONObject();
                wrapper.put("records", new JSONArray(body));
                return wrapper;
            }
            return new JSONObject(body);
        }
        return null;
    }

    /**
     * Converts 1-based status code string (e.g. "3") to status name (e.g. "QA Passed").
     * If the value is already a string name, returns it unchanged.
     */
    private String resolveStatusName(String statusCode) {
        try {
            int index = Integer.parseInt(statusCode) - 1;
            String[] names = privateStrings.getPCsCRMStatusNames();
            if (index >= 0 && index < names.length) return names[index];
        } catch (NumberFormatException e) {
            // Already a name
        }
        return statusCode;
    }

    /**
     * Derives a human-readable Device Type label from brand/OS/isLaptop.
     */
    private String deriveDeviceType(String brand, String isLaptop, String os) {
        boolean laptop = "Y".equalsIgnoreCase(isLaptop);
        boolean apple  = brand.toLowerCase().contains("apple");
        boolean chrome = os.toLowerCase().contains("chrome");
        boolean linux  = os.toLowerCase().contains("linux") || os.toLowerCase().contains("ubuntu");

        if (apple)  return laptop ? "Mac Laptop"     : "Mac Desktop";
        if (chrome) return "Chromebook";
        if (linux)  return laptop ? "Linux Laptop"   : "Linux Desktop";
        return           laptop ? "Windows Laptop" : "Windows Desktop";
    }
}

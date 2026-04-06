/*
 *
 * MIT License
 *
 * Copyright (c) 2024 Executive Undertakings
 *
 * Fork of Java-QA-Helper by PCs for People & Free Geek
 * Original: https://github.com/freegeek-pdx/Java-QA-Helper
 *
 * This file replaces the proprietary PrivateStrings class with a custom
 * configuration pointing to files.executiveundertakings.com for updates.
 * Free Geek-specific integrations (production log, CRM, email API,
 * WiFi auto-connect) are stubbed out to return empty/no-op values.
 *
 */

package GUI;

import java.util.LinkedHashMap;

public class CustomStrings {

    // -------------------------------------------------------------------------
    // UPDATE SERVER
    // All update checks and downloads are served from files.executiveundertakings.com
    // -------------------------------------------------------------------------

    /** Base URL for the update server. Must end with a trailing slash. */
    public static final String UPDATE_BASE_URL = "https://files.executiveundertakings.com/qa-updates/";

    /** URL to a plain-text file containing just the latest version string, e.g. "2026.1.7-1" */
    public static final String LATEST_VERSION_URL = UPDATE_BASE_URL + "latest-version.txt";

    // -------------------------------------------------------------------------
    // CONFIG PASSWORD
    // Used to encrypt/decrypt the local config ZIP file on each machine.
    // Change this to any password you want to use for your fork.
    // -------------------------------------------------------------------------

    public String getConfigPassword() {
        return "execundertakings-qa";
    }

    // -------------------------------------------------------------------------
    // CONDITION GRADES
    // Grades used for labelling device condition.  The key is the grade letter
    // and the value is its description (can be empty).
    // -------------------------------------------------------------------------

    public LinkedHashMap<String, String> getFreeGeekConditionGradesAndDescriptions() {
        LinkedHashMap<String, String> grades = new LinkedHashMap<>();
        grades.put("A", "Excellent — minimal wear");
        grades.put("B", "Good — minor cosmetic wear");
        grades.put("C", "Fair — visible wear, fully functional");
        grades.put("D", "Poor — heavy wear or cosmetic damage");
        return grades;
    }

    // -------------------------------------------------------------------------
    // CRM / PRODUCTION LOG — all stubbed out (not used in this fork)
    // -------------------------------------------------------------------------

    /** Returns empty string to disable spec logging to a remote server. */
    public String getLogSpecsURL() {
        return "";
    }

    /** Returns empty string to disable production log integration. */
    public String getAddToFreeGeekProductionLogURL(boolean isTestMode) {
        return "";
    }

    /** Returns empty string to disable all Free Geek API calls. */
    public String getFreeGeekAPIurl(String type, boolean isTestMode) {
        return "";
    }

    /** Returns empty string to disable Free Geek Specs page links. */
    public String getFreeGeekSpecsURL(String pid, boolean isTestMode) {
        return "";
    }

    /** Returns empty string to disable Absolute logging. */
    public String getLogAbsoluteEnabledURL(boolean isTestMode) {
        return "";
    }

    /** Returns empty string to disable remote-managed Mac logging. */
    public String getLogRemoteManagedMacURL() {
        return "";
    }

    // -------------------------------------------------------------------------
    // EMAIL — stubbed out
    // -------------------------------------------------------------------------

    public String getEmailPrivateKey() {
        return "";
    }

    public String getEmailFromAddress() {
        return "";
    }

    public String getEmailToAddress() {
        return "";
    }

    // -------------------------------------------------------------------------
    // PASSWORDS
    // Only getGenericPassword() is set; the rest are empty so they never
    // match, keeping the FG-specific password rotation logic inert.
    // -------------------------------------------------------------------------

    public String getInsecureFreeGeekPassword() {
        return "";
    }

    public String getCurrentFreeGeekPassword() {
        return "";
    }

    public String getPreviousFreeGeekPassword() {
        return "";
    }

    public String getOlderFreeGeekPassword() {
        return "";
    }

    /**
     * Generic admin password tried when unlocking configuration mode.
     * Set this to whatever admin password is used on your refurb machines.
     */
    public String getGenericPassword() {
        return "";
    }

    public String getInternPassword() {
        return "";
    }

    // -------------------------------------------------------------------------
    // CRM STATUS NAMES
    // These 13 status names map to indices 0-12 used throughout QAHelper.java.
    // Index 3  → the "repair" status (used in "Reopen Repair" menu item)
    // Index 12 → the final completion status (used in "Done Testing" flow)
    // Keep these strings consistent with whatever CRM you use, or leave them
    // as-is if you are not using the CRM workflow.
    // -------------------------------------------------------------------------

    public String[] getPCsCRMStatusNames() {
        return new String[]{
            "Received",           // 0
            "In Grading",         // 1
            "QA Passed",          // 2
            "In Repair",          // 3  ← "Reopen Repair" target
            "Repaired",           // 4
            "QA Failed",          // 5
            "Parts Harvested",    // 6
            "Recycled",           // 7
            "On Hold",            // 8
            "Donated",            // 9
            "Sold",               // 10
            "Returned",           // 11
            "For Sale"            // 12 ← final completion status
        };
    }

    /**
     * Status names that trigger an automatic status change when a technician
     * logs in.  Return an empty array to disable the auto-change behaviour.
     */
    public String[] getPCsCRMStatusNamesToAutoChangeOnLogin() {
        return new String[]{};
    }

    /**
     * Product types available in the CRM for the given device type string
     * (e.g. "Mac Desktop", "PC Laptop").  Returns an empty map to disable
     * the product-type selection dialog.
     */
    public LinkedHashMap<String, String> getFreeGeekProductTypesForDeviceTypeInPCsCRM(String deviceType) {
        return new LinkedHashMap<>();
    }

    // -------------------------------------------------------------------------
    // WIFI — stubbed out (no auto-connect to a specific network)
    // -------------------------------------------------------------------------

    public String getFreeGeekWiFiName() {
        return "";
    }

    public String getFreeGeekWiFiPassword() {
        return "";
    }

    // -------------------------------------------------------------------------
    // CRM STATUS HISTORY COLUMN NAMES
    // Display names shown as table headers in the Status History window.
    // Raw names are the JSON field keys returned by the CRM API.
    // Since CRM is stubbed, these values are placeholders — the window will
    // show "UNKNOWN DATE / STATUS / TECH" when no real data is available.
    // -------------------------------------------------------------------------

    /** Returns column display names for the status history table. */
    public String[] getPCsCRMStatusHistoryDisplayColumnNames() {
        return new String[]{"Date", "Status", "Technician"};
    }

    /** Returns raw JSON field names used to parse CRM status history responses. */
    public String[] getPCsCRMStatusHistoryRawColumnNames() {
        return new String[]{"date", "status", "username"};
    }

}
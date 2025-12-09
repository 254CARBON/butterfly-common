package com.z254.butterfly.common.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for API versioning.
 * 
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * butterfly:
 *   api:
 *     versioning:
 *       enabled: true
 *       current-version: "1.2.0"
 *       supported-versions:
 *         - "v1"
 *         - "v1.1"
 *         - "v1.2"
 *       strict: false
 *       log-warnings: true
 *       deprecated-endpoints:
 *         /api/v1/old-endpoint:
 *           sunset-date: "2025-06-01"
 *           successor: "/api/v1/new-endpoint"
 *           notice: "Use the new endpoint for improved performance"
 * }</pre>
 * 
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "butterfly.api.versioning")
public class ApiVersionProperties {
    
    /**
     * Enable API versioning filter.
     */
    private boolean enabled = true;
    
    /**
     * Current API version string.
     */
    private String currentVersion = "1.0.0";
    
    /**
     * Set of supported API versions.
     */
    private Set<String> supportedVersions = Set.of("v1");
    
    /**
     * Strict version validation - reject unsupported versions with 400.
     */
    private boolean strict = false;
    
    /**
     * Log warnings for deprecated endpoint access.
     */
    private boolean logWarnings = true;
    
    /**
     * Map of deprecated endpoints to their metadata.
     */
    private Map<String, DeprecatedEndpoint> deprecatedEndpoints = new HashMap<>();
    
    /**
     * Header name for version request (default: Accept-Version).
     */
    private String versionHeader = "Accept-Version";
    
    /**
     * Header name for version response (default: X-API-Version).
     */
    private String responseVersionHeader = "X-API-Version";
    
    // Getters and Setters
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }
    
    public Set<String> getSupportedVersions() {
        return supportedVersions;
    }
    
    public void setSupportedVersions(Set<String> supportedVersions) {
        this.supportedVersions = supportedVersions;
    }
    
    public boolean isStrict() {
        return strict;
    }
    
    public void setStrict(boolean strict) {
        this.strict = strict;
    }
    
    public boolean isLogWarnings() {
        return logWarnings;
    }
    
    public void setLogWarnings(boolean logWarnings) {
        this.logWarnings = logWarnings;
    }
    
    public Map<String, DeprecatedEndpoint> getDeprecatedEndpoints() {
        return deprecatedEndpoints;
    }
    
    public void setDeprecatedEndpoints(Map<String, DeprecatedEndpoint> deprecatedEndpoints) {
        this.deprecatedEndpoints = deprecatedEndpoints;
    }
    
    public String getVersionHeader() {
        return versionHeader;
    }
    
    public void setVersionHeader(String versionHeader) {
        this.versionHeader = versionHeader;
    }
    
    public String getResponseVersionHeader() {
        return responseVersionHeader;
    }
    
    public void setResponseVersionHeader(String responseVersionHeader) {
        this.responseVersionHeader = responseVersionHeader;
    }
    
    /**
     * Metadata for a deprecated endpoint.
     */
    public static class DeprecatedEndpoint {
        
        /**
         * Date when the endpoint will be removed (ISO 8601 format or HTTP-date).
         */
        private String sunsetDate;
        
        /**
         * URI of the successor endpoint.
         */
        private String successor;
        
        /**
         * Human-readable deprecation notice.
         */
        private String notice;
        
        public DeprecatedEndpoint() {}
        
        public DeprecatedEndpoint(String sunsetDate, String successor, String notice) {
            this.sunsetDate = sunsetDate;
            this.successor = successor;
            this.notice = notice;
        }
        
        public String getSunsetDate() {
            return sunsetDate;
        }
        
        public void setSunsetDate(String sunsetDate) {
            this.sunsetDate = sunsetDate;
        }
        
        public String getSuccessor() {
            return successor;
        }
        
        public void setSuccessor(String successor) {
            this.successor = successor;
        }
        
        public String getNotice() {
            return notice;
        }
        
        public void setNotice(String notice) {
            this.notice = notice;
        }
    }
}

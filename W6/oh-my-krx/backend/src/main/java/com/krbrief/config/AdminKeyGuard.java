package com.krbrief.config;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Admin authentication guard supporting both key-based and trusted CIDR-based access.
 *
 * <p>When ADMIN_KEY is set, admin access is granted if:
 * <ul>
 *   <li>Request includes matching X-Admin-Key header or adminKey query param, OR</li>
 *   <li>Request remote address matches a trusted CIDR (default: 127.0.0.1/32, ::1/128)</li>
 * </ul>
 *
 * <p>For production deploys, review APP_ADMIN_TRUSTED_CIDRS carefully or disable local bypass.
 */
@Component
public class AdminKeyGuard {
  private static final Logger log = LoggerFactory.getLogger(AdminKeyGuard.class);
  private static final String DEFAULT_TRUSTED_CIDRS = "127.0.0.1/32,::1/128";

  private final String adminKey;
  private final List<String> trustedCidrStrings;

  public AdminKeyGuard(
      @Value("${app.adminKey:}") String adminKey,
      @Value("${app.adminTrustedCidrs:" + DEFAULT_TRUSTED_CIDRS + "}") String trustedCidrs) {
    this.adminKey = adminKey == null ? "" : adminKey.trim();
    this.trustedCidrStrings =
        trustedCidrs == null || trustedCidrs.isBlank()
            ? Collections.emptyList()
            : Arrays.stream(trustedCidrs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    log.info("AdminKeyGuard initialized: adminEnabled={}, trustedCidrs={}", adminEnabled(), trustedCidrStrings);
  }

  public boolean adminEnabled() {
    return !adminKey.isBlank();
  }

  /**
   * Checks if the request has admin privileges.
   *
   * <p>Admin access is granted if:
   * <ol>
   *   <li>Admin is disabled (no ADMIN_KEY configured), OR</li>
   *   <li>Request includes valid admin key (header or query param), OR</li>
   *   <li>Request remote address matches a trusted CIDR</li>
   * </ol>
   */
  public boolean isAdmin(HttpServletRequest request) {
    if (!adminEnabled()) return true;

    // Key-based auth
    String header = request.getHeader("X-Admin-Key");
    if (header != null && header.equals(adminKey)) return true;

    String q = request.getParameter("adminKey");
    if (q != null && q.equals(adminKey)) return true;

    // Trusted CIDR-based auth
    return isTrustedRemoteAddr(request);
  }

  /**
   * Checks if the request's remote address matches any configured trusted CIDR.
   */
  boolean isTrustedRemoteAddr(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return false;
    }

    for (String cidr : trustedCidrStrings) {
      try {
        IPAddress trustedNetwork = new IPAddressString(cidr).getAddress();
        IPAddress remoteIp = new IPAddressString(remoteAddr).getAddress();

        if (trustedNetwork != null && remoteIp != null && trustedNetwork.contains(remoteIp)) {
          log.debug("Admin access granted via trusted CIDR: {} matches {}", remoteAddr, cidr);
          return true;
        }
      } catch (Exception e) {
        log.warn("Failed to parse CIDR '{}' or remote address '{}': {}", cidr, remoteAddr, e.getMessage());
      }
    }
    return false;
  }

  /** Visible for testing. */
  List<String> getTrustedCidrStrings() {
    return Collections.unmodifiableList(trustedCidrStrings);
  }
}

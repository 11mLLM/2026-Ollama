package com.krbrief.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AdminKeyGuardTest {

  private HttpServletRequest mockRequest(String remoteAddr) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRemoteAddr()).thenReturn(remoteAddr);
    when(req.getHeader("X-Admin-Key")).thenReturn(null);
    when(req.getParameter("adminKey")).thenReturn(null);
    return req;
  }

  private HttpServletRequest mockRequestWithKey(String remoteAddr, String key) {
    HttpServletRequest req = mockRequest(remoteAddr);
    when(req.getHeader("X-Admin-Key")).thenReturn(key);
    return req;
  }

  @Nested
  @DisplayName("When ADMIN_KEY is not set")
  class NoAdminKey {
    @Test
    @DisplayName("always allows admin access")
    void alwaysAllows() {
      AdminKeyGuard guard = new AdminKeyGuard("", "127.0.0.1/32");
      HttpServletRequest req = mockRequest("192.168.1.1");
      assertTrue(guard.isAdmin(req));
      assertTrue(guard.isAdmin(mockRequest("10.0.0.1")));
    }
  }

  @Nested
  @DisplayName("Key-based authentication")
  class KeyBasedAuth {
    @Test
    @DisplayName("allows access with valid header")
    void allowsValidHeader() {
      AdminKeyGuard guard = new AdminKeyGuard("secret123", "");
      HttpServletRequest req = mockRequestWithKey("10.0.0.1", "secret123");
      assertTrue(guard.isAdmin(req));
    }

    @Test
    @DisplayName("denies access with invalid header")
    void deniesInvalidHeader() {
      AdminKeyGuard guard = new AdminKeyGuard("secret123", "127.0.0.1/32");
      HttpServletRequest req = mockRequestWithKey("10.0.0.1", "wrong");
      assertFalse(guard.isAdmin(req));
    }

    @Test
    @DisplayName("allows access with valid query param")
    void allowsValidQueryParam() {
      AdminKeyGuard guard = new AdminKeyGuard("secret123", "127.0.0.1/32");
      HttpServletRequest req = mockRequest("10.0.0.1");
      when(req.getParameter("adminKey")).thenReturn("secret123");
      assertTrue(guard.isAdmin(req));
    }
  }

  @Nested
  @DisplayName("Trusted CIDR matching")
  class TrustedCidrMatch {
    @Test
    @DisplayName("matches 127.0.0.1 in 127.0.0.1/32")
    void matchesLocalhostIpv4() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "127.0.0.1/32");
      HttpServletRequest req = mockRequest("127.0.0.1");
      assertTrue(guard.isAdmin(req));
    }

    @Test
    @DisplayName("matches ::1 in ::1/128")
    void matchesLocalhostIpv6() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "::1/128");
      HttpServletRequest req = mockRequest("::1");
      assertTrue(guard.isAdmin(req));
    }

    @Test
    @DisplayName("matches default trusted CIDRs")
    void matchesDefaultCidrs() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "127.0.0.1/32,::1/128");
      assertTrue(guard.isAdmin(mockRequest("127.0.0.1")));
      assertTrue(guard.isAdmin(mockRequest("::1")));
    }

    @Test
    @DisplayName("matches Docker internal 172.16.0.0/12 range")
    void matchesDockerInternalRange() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "172.16.0.0/12");
      assertTrue(guard.isAdmin(mockRequest("172.16.0.1")));
      assertTrue(guard.isAdmin(mockRequest("172.20.30.40")));
      assertTrue(guard.isAdmin(mockRequest("172.31.255.255")));
    }

    @Test
    @DisplayName("denies non-trusted IP")
    void deniesNonTrustedIp() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "127.0.0.1/32");
      HttpServletRequest req = mockRequest("192.168.1.1");
      assertFalse(guard.isAdmin(req));
    }

    @Test
    @DisplayName("matches any of multiple CIDRs")
    void matchesMultipleCidrs() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "10.0.0.0/8,192.168.0.0/16");
      assertTrue(guard.isAdmin(mockRequest("10.5.6.7")));
      assertTrue(guard.isAdmin(mockRequest("192.168.1.100")));
      assertFalse(guard.isAdmin(mockRequest("172.16.0.1")));
    }
  }

  @Nested
  @DisplayName("isTrustedRemoteAddr")
  class IsTrustedRemoteAddrMethod {
    @Test
    @DisplayName("returns false for null remote address")
    void nullRemoteAddr() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "127.0.0.1/32");
      HttpServletRequest req = mock(HttpServletRequest.class);
      when(req.getRemoteAddr()).thenReturn(null);
      assertFalse(guard.isTrustedRemoteAddr(req));
    }

    @Test
    @DisplayName("returns false for empty remote address")
    void emptyRemoteAddr() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "127.0.0.1/32");
      HttpServletRequest req = mock(HttpServletRequest.class);
      when(req.getRemoteAddr()).thenReturn("");
      assertFalse(guard.isTrustedRemoteAddr(req));
    }
  }

  @Nested
  @DisplayName("Configuration parsing")
  class ConfigParsing {
    @Test
    @DisplayName("parses comma-separated CIDRs")
    void parsesCommaSeparatedCidrs() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "10.0.0.0/8, 172.16.0.0/12 , 192.168.0.0/16");
      assertEquals(3, guard.getTrustedCidrStrings().size());
      assertTrue(guard.getTrustedCidrStrings().contains("10.0.0.0/8"));
      assertTrue(guard.getTrustedCidrStrings().contains("172.16.0.0/12"));
      assertTrue(guard.getTrustedCidrStrings().contains("192.168.0.0/16"));
    }

    @Test
    @DisplayName("handles empty trusted CIDRs config")
    void handlesEmptyConfig() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", "");
      assertTrue(guard.getTrustedCidrStrings().isEmpty());
    }

    @Test
    @DisplayName("handles null trusted CIDRs config")
    void handlesNullConfig() {
      AdminKeyGuard guard = new AdminKeyGuard("secret", null);
      assertTrue(guard.getTrustedCidrStrings().isEmpty());
    }
  }
}

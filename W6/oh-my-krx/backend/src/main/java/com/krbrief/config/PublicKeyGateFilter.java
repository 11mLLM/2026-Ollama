package com.krbrief.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicKeyGateFilter extends OncePerRequestFilter {
  private final String publicKey;

  public PublicKeyGateFilter(@Value("${app.publicKey:}") String publicKey) {
    this.publicKey = publicKey == null ? "" : publicKey.trim();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // Only gate API routes on the backend.
    String path = request.getRequestURI();
    return !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (publicKey.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    String k = request.getParameter("k");
    if (k == null || !k.equals(publicKey)) {
      response.setStatus(401);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"missing_or_invalid_key\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }
}


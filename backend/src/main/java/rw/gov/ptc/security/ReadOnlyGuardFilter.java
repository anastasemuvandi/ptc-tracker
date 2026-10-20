package rw.gov.ptc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import rw.gov.ptc.auth.api.AuthPrincipal;

import java.io.IOException;
import java.util.Set;

/** Read-only users (internal audit) can read everything and change nothing. */
public class ReadOnlyGuardFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p && p.isReadOnlyOnly()
                && !SAFE.contains(request.getMethod()) && !request.getRequestURI().startsWith("/api/auth/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"read_only\",\"message\":\"Read-only access\",\"params\":{}}");
            return;
        }
        chain.doFilter(request, response);
    }
}

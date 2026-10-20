package rw.gov.ptc.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.InvalidTokenException;
import rw.gov.ptc.auth.api.TokenVerifier;
import rw.gov.ptc.tenancy.TenantContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the bearer token and establishes the tenant scope for the
 * request. From here on, every database connection carries
 * app.institution_id from the token (see TenantAwareDataSource).
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private final TokenVerifier verifier;

    public BearerTokenFilter(TokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        AuthPrincipal principal;
        try {
            principal = verifier.verify(header.substring(7).trim());
        } catch (InvalidTokenException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            response.getWriter().write("{\"code\":\"auth.invalid_token\",\"message\":\"Session expired\",\"params\":{}}");
            return;
        }
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        principal.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.name())));
        if (!principal.committees().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COMMITTEE_MEMBER"));
        }
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.set(new TenantContext.Scope(principal.institutionId(), principal.userId(),
                principal.isPlatformAdmin()));
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}

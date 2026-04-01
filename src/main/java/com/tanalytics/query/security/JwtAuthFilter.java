package com.tanalytics.query.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Extracts a Bearer JWT from the Authorization header, validates it,
 * and populates the Spring Security context.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        if (!jwtService.isTokenValid(token) || !jwtService.isAccessToken(token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid access token");
            return;
        }

        Optional<String> requestedSiteId = extractRequestedSiteId(request.getRequestURI());
        if (requestedSiteId.isPresent()) {
            List<String> allowedSiteIds = jwtService.extractSiteIds(token);
            if (!containsSiteId(allowedSiteIds, requestedSiteId.get())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden for requested site");
                return;
            }
        }

        String subject = jwtService.extractSubject(token);
        List<SimpleGrantedAuthority> authorities = jwtService.extractRole(token)
                .map(role -> List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT))))
                .orElse(List.of());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(subject, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractRequestedSiteId(String requestUri) {
        String prefix = "/api/v1/sites/";
        if (!requestUri.startsWith(prefix)) {
            return Optional.empty();
        }

        String remainder = requestUri.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex <= 0) {
            return Optional.empty();
        }

        return Optional.of(remainder.substring(0, slashIndex));
    }

    private boolean containsSiteId(List<String> allowedSiteIds, String requestedSiteId) {
        for (String allowedSiteId : allowedSiteIds) {
            if (requestedSiteId.equalsIgnoreCase(allowedSiteId)) {
                return true;
            }
        }
        return false;
    }
}


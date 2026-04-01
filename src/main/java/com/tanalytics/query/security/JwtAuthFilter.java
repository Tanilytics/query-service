package com.tanalytics.query.security;

import com.tanalytics.query.auth.InternalAuthClient;
import com.tanalytics.query.auth.InternalAuthUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.UUID;

/**
 * Extracts a Bearer JWT from the Authorization header, validates it,
 * and populates the Spring Security context.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final InternalAuthClient internalAuthClient;

    public JwtAuthFilter(JwtService jwtService, InternalAuthClient internalAuthClient) {
        this.jwtService = jwtService;
        this.internalAuthClient = internalAuthClient;
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

        if (request.getRequestURI().startsWith("/api/v1/sites/")
                && extractRequestedSiteId(request.getRequestURI()).isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid site identifier");
            return;
        }

        Optional<UUID> requestedSiteId = extractRequestedSiteId(request.getRequestURI());
        if (requestedSiteId.isPresent()) {
            Optional<UUID> userId = jwtService.extractUserId(token);
            if (userId.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token subject");
                return;
            }

            try {
                boolean member = internalAuthClient.isMember(requestedSiteId.get(), userId.get());
                if (!member) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden for requested site");
                    return;
                }
            } catch (InternalAuthUnavailableException ex) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Unable to verify site membership");
                return;
            }

            List<String> claimedSiteIds = jwtService.extractSiteIds(token);
            if (!claimedSiteIds.isEmpty() && !containsSiteId(claimedSiteIds, requestedSiteId.get().toString())) {
                log.debug("Token siteIds mismatch for userId={} siteId={} claimCount={} but membership check passed",
                        userId.get(), requestedSiteId.get(), claimedSiteIds.size());
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

    private Optional<UUID> extractRequestedSiteId(String requestUri) {
        String prefix = "/api/v1/sites/";
        if (!requestUri.startsWith(prefix)) {
            return Optional.empty();
        }

        String remainder = requestUri.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        if (slashIndex <= 0) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(remainder.substring(0, slashIndex)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
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


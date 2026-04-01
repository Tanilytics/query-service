package com.tanalytics.query.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

public record InternalMembershipResponse(
        UUID userId,
        UUID siteId,
        @JsonAlias({"member", "allowed"}) boolean allowed,
        String role
) {}

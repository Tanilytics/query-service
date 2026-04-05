package com.tanalytics.query.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalMembershipResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesAllowedPropertyFromAuthServiceContract() throws Exception {
        String json = """
                {
                  "userId": "33333333-3333-3333-3333-333333333333",
                  "siteId": "11111111-1111-1111-1111-111111111111",
                  "allowed": true,
                  "role": "admin"
                }
                """;

        InternalMembershipResponse response = objectMapper.readValue(json, InternalMembershipResponse.class);

        assertTrue(response.allowed());
    }

    @Test
    void deserializesLegacyMemberAliasForBackwardCompatibility() throws Exception {
        String json = """
                {
                  "userId": "33333333-3333-3333-3333-333333333333",
                  "siteId": "11111111-1111-1111-1111-111111111111",
                  "member": false,
                  "role": "viewer"
                }
                """;

        InternalMembershipResponse response = objectMapper.readValue(json, InternalMembershipResponse.class);

        assertFalse(response.allowed());
    }
}

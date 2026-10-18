package com.housekeeping.management.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class PortalIdentityTest {
    @Test
    void canonicalEmailResolvesDatabaseIdentityInsteadOfSubmittedRole() throws Exception {
        JdbcTemplate jdbc = databaseWithMatches("FROM Staff");
        PortalUser user = (PortalUser) new PortalIdentityService(jdbc).loadUserByUsername("  USER@EXAMPLE.TEST  ");
        assertThat(user.getRole()).isEqualTo("STAFF");
        assertThat(user.getAccountId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("user@example.test");
    }

    @Test
    void missingOrAmbiguousCrossRoleEmailCannotAuthenticate() throws Exception {
        assertThatThrownBy(() -> new PortalIdentityService(databaseWithMatches("NONE")).loadUserByUsername("user@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> new PortalIdentityService(databaseWithMatches("FROM")).loadUserByUsername("user@example.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void passwordEncoderAcceptsBcryptButDoesNotAllowPlaintextFallback() {
        var encoder = new PortalSecurityConfiguration().passwordEncoder();
        String encoded = encoder.encode("test-password");
        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(encoder.matches("test-password", encoded)).isTrue();
        assertThat(encoder.matches("test-password", encoded.substring("{bcrypt}".length()))).isTrue();
        assertThat(encoder.matches("test-password", "{noop}test-password")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate databaseWithMatches(String match) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(7L);
        when(rs.getString(2)).thenReturn("user@example.test");
        when(rs.getString(3)).thenReturn("stored-hash");
        when(rs.getString(4)).thenReturn("Database identity");
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper<PortalUser> mapper = invocation.getArgument(1);
            return sql.contains(match) ? List.of(mapper.mapRow(rs, 0)) : List.of();
        }).when(jdbc).query(anyString(), any(RowMapper.class), eq("user@example.test"));
        return jdbc;
    }
}

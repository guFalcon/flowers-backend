package info.unterrainer.htl.resources;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class AdminTokenFilterTest {

    @Test
    void configuredTokenAcceptsOnlyItself() {
        AdminTokenFilter filter = filterWithToken(Optional.of("s3cret"));

        assertThat(filter.accepts("s3cret")).isTrue();
        assertThat(filter.accepts("true")).isFalse();
        assertThat(filter.accepts("s3cret ")).isFalse();
        assertThat(filter.accepts(null)).isFalse();
    }

    @Test
    void missingTokenRefusesEverything() {
        AdminTokenFilter filter = filterWithToken(Optional.empty());

        assertThat(filter.accepts("")).isFalse();
        assertThat(filter.accepts("anything")).isFalse();
        assertThat(filter.accepts(null)).isFalse();
    }

    @Test
    void blankTokenRefusesEverything() {
        AdminTokenFilter filter = filterWithToken(Optional.of("  "));

        assertThat(filter.accepts("  ")).isFalse();
        assertThat(filter.accepts("")).isFalse();
    }

    private static AdminTokenFilter filterWithToken(Optional<String> token) {
        AdminTokenFilter filter = new AdminTokenFilter();
        filter.adminToken = token;
        return filter;
    }
}

package org.soa8.freight.common.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DigestUtilsTest {

    @Test
    void shouldProduceDeterministicSha256() {
        String input = "test-payload-for-digest";
        String hash1 = DigestUtils.sha256Hex(input);
        String hash2 = DigestUtils.sha256Hex(input);

        assertThat(hash1).isNotEmpty();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }
}

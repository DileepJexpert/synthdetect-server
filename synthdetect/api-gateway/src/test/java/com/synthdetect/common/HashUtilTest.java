package com.synthdetect.common;

import com.synthdetect.common.util.HashUtil;
import com.synthdetect.common.util.IdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HashUtilTest {

    @Test
    void sha256_shouldProduceConsistentHash() {
        String input = "sd_live_testkey12345";
        String hash1 = HashUtil.sha256(input);
        String hash2 = HashUtil.sha256(input);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex chars
    }

    @Test
    void sha256_shouldProduceDifferentHashesForDifferentInputs() {
        String hash1 = HashUtil.sha256("input1");
        String hash2 = HashUtil.sha256("input2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void generateSecureRandomString_shouldProduceCorrectLength() {
        String random = HashUtil.generateSecureRandomString(32);
        assertThat(random).hasSize(32);
    }

    @Test
    void generateSecureRandomString_shouldProduceUniqueStrings() {
        String random1 = HashUtil.generateSecureRandomString(32);
        String random2 = HashUtil.generateSecureRandomString(32);

        assertThat(random1).isNotEqualTo(random2);
    }

    @Test
    void idGenerator_detectionId_shouldHaveCorrectPrefix() {
        String id = IdGenerator.detectionId();
        assertThat(id).startsWith("det_");
        assertThat(id).hasSize(10); // "det_" + 6 chars
    }

    @Test
    void idGenerator_batchId_shouldHaveCorrectPrefix() {
        String id = IdGenerator.batchId();
        assertThat(id).startsWith("bat_");
        assertThat(id).hasSize(10);
    }

    @Test
    void idGenerator_requestId_shouldHaveCorrectPrefix() {
        String id = IdGenerator.requestId();
        assertThat(id).startsWith("req_");
        assertThat(id).hasSize(10);
    }

    @Test
    void idGenerator_shouldProduceUniqueIds() {
        String id1 = IdGenerator.detectionId();
        String id2 = IdGenerator.detectionId();
        assertThat(id1).isNotEqualTo(id2);
    }
}

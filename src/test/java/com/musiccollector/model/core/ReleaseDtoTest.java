package com.musiccollector.model.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseDtoTest {

    private static ReleaseDto with(String label, String catalogNumber, String country) {
        return new ReleaseDto(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Remain in Light", "Talking Heads", 1980, Format.VINYL,
                label, catalogNumber, country, null, null, null);
    }

    @Test
    void buildsTheSearchResultSubtitle() {
        assertThat(with("Sire", "SRK 6095", "US").disambiguation()).isEqualTo("Sire · SRK 6095 · US");
    }

    @Test
    void omitsMissingPartsRatherThanLeavingDanglingSeparators() {
        assertThat(with("Sire", null, "US").disambiguation()).isEqualTo("Sire · US");
        assertThat(with(null, null, "US").disambiguation()).isEqualTo("US");
        assertThat(with("Sire", "  ", null).disambiguation()).isEqualTo("Sire");
    }

    @Test
    void isEmptyWhenNothingIsKnown() {
        assertThat(with(null, null, null).disambiguation()).isEmpty();
    }
}

package com.rekordo.model.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormatTest {

    @ParameterizedTest
    @CsvSource({
        "Vinyl,VINYL",
        "12\" Vinyl,VINYL",
        "7\" Flexi-disc,VINYL",
        "Shellac,VINYL",
        "CD,CD",
        "Enhanced CD,CD",
        "SACD,CD",
        "Cassette,CASSETTE",
        "Digital Media,DIGITAL",
        "DAT,OTHER",
    })
    void mapsMusicBrainzMediaFormats(String mediaFormat, Format expected) {
        assertThat(Format.fromMediumName(mediaFormat)).isEqualTo(expected);
    }

    @Test
    void treatsMissingFormatAsOther() {
        assertThat(Format.fromMediumName(null)).isEqualTo(Format.OTHER);
        assertThat(Format.fromMediumName("  ")).isEqualTo(Format.OTHER);
    }

    @Test
    void prefersCassetteOverCdWhenBothWordsAppear() {
        // "CD" is checked last precisely so a compound name is not misfiled as a disc.
        assertThat(Format.fromMediumName("Cassette (CD-sized case)")).isEqualTo(Format.CASSETTE);
    }
}

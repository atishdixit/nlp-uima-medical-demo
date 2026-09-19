package com.example.mednlp.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalizedTextTest {

    @Test
    void collapsesWhitespaceRunsAndMapsOffsetsBack() {
        String original = "a  b\n\tc d";
        NormalizedText nt = NormalizedText.of(original);

        assertThat(nt.text()).isEqualTo("a b c d");
        // 'b' is the 3rd normalised char and the 4th original char
        assertThat(nt.toOriginalStart(2)).isEqualTo(3);
        assertThat(nt.toOriginalEnd(3)).isEqualTo(4);
        // "d" is last in both
        assertThat(nt.toOriginalStart(6)).isEqualTo(original.length() - 1);
    }

    @Test
    void mapsOriginalOffsetsForward() {
        NormalizedText nt = NormalizedText.of("chest    pain");
        assertThat(nt.text()).isEqualTo("chest pain");
        assertThat(nt.fromOriginalStart(9)).isEqualTo(6);      // 'p'
        assertThat(nt.fromOriginalEnd(13)).isEqualTo(10);      // end of "pain"
        assertThat(nt.fromOriginalStart(6)).isEqualTo(5);      // a collapsed space maps to the single space
    }

    @Test
    void textWithoutExtraWhitespaceIsUnchanged() {
        NormalizedText nt = NormalizedText.of("no change here");
        assertThat(nt.text()).isEqualTo("no change here");
        for (int i = 0; i < nt.text().length(); i++) {
            assertThat(nt.toOriginalStart(i)).isEqualTo(i);
        }
    }

    @Test
    void emptyInputIsHandled() {
        assertThat(NormalizedText.of("").text()).isEmpty();
    }
}

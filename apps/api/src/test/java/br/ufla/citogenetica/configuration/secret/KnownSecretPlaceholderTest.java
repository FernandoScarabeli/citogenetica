package br.ufla.citogenetica.configuration.secret;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnownSecretPlaceholderTest {

    @Test
    void rejectsKnownMarkersWithoutCaseOrPaddingSensitivity() {
        assertThat(KnownSecretPlaceholder.isPlaceholder("troque-isto")).isTrue();
        assertThat(KnownSecretPlaceholder.isPlaceholder("  TrOqUe-IsTo-extra  ")).isTrue();
        assertThat(KnownSecretPlaceholder.isPlaceholder("SEU-SEGREDO-DE-EXEMPLO")).isTrue();
        assertThat(KnownSecretPlaceholder.isPlaceholder("  Password  ")).isTrue();
    }

    @Test
    void acceptsAConfiguredRandomLookingValue() {
        assertThat(KnownSecretPlaceholder.isPlaceholder("c03a84e6548df9149be7a1d3")).isFalse();
    }

    @Test
    void treatsNullAsMissingRatherThanAsAPlaceholder() {
        assertThat(KnownSecretPlaceholder.isPlaceholder(null)).isFalse();
    }
}

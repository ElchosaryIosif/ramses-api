package gr.ramsesbazaar.core.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void transliteratesGreek() {
        assertThat(Slugs.nameSlug("Ασημένιο βραχιόλι")).isEqualTo("asimenio-vrachioli");
        assertThat(Slugs.nameSlug("Αιγυπτιακό θυμίαμα ΜΟΥΣΚ")).isEqualTo("aigyptiako-thymiama-mousk");
        assertThat(Slugs.nameSlug("Ψάθινη τσάντα")).isEqualTo("psathini-tsanta");
    }

    @Test
    void keepsLatinAndDigitsAndDropsTheRest() {
        assertThat(Slugs.nameSlug("Boho Dress — size M (2025)")).isEqualTo("boho-dress-size-m-2025");
        assertThat(Slugs.nameSlug("Café Été")).isEqualTo("cafe-ete");
        assertThat(Slugs.nameSlug("✦✦✦")).isEmpty();
    }

    @Test
    void addsTheIdAndCapsTheLength() {
        assertThat(Slugs.of("Scarab necklace", "k3x9a")).isEqualTo("scarab-necklace-k3x9a");
        assertThat(Slugs.of("✦", "k3x9a")).isEqualTo("k3x9a");
        assertThat(Slugs.nameSlug("word ".repeat(30))).hasSizeLessThanOrEqualTo(60).doesNotEndWith("-");
    }

    @Test
    void matchesCurrentAndOldAddresses() {
        assertThat(Slugs.matches("scarab-necklace-k3x9a", "k3x9a")).isTrue();
        assertThat(Slugs.matches("k3x9a", "k3x9a")).isTrue();
        assertThat(Slugs.matches("scarab-necklace-xk3x9a", "k3x9a")).isFalse();
    }
}

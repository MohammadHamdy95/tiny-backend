package app.hamdy.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void generatesSevenCharBase62Slugs() {
        for (int i = 0; i < 1000; i++) {
            assertThat(Slugs.generate()).matches("[A-Za-z0-9]{7}");
        }
    }

    @Test
    void reservedWordsAreCaseInsensitive() {
        assertThat(Slugs.reserved("api")).isTrue();
        assertThat(Slugs.reserved("API")).isTrue();
        assertThat(Slugs.reserved("robots.txt")).isTrue();
        assertThat(Slugs.reserved("my-link")).isFalse();
    }
}

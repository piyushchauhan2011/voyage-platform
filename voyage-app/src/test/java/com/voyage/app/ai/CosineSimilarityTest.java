package com.voyage.app.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Cosine similarity is the measure the embedding rung teaches, so it is worth pinning down
 * on vectors small enough to verify by hand.
 */
class CosineSimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        float[] vector = {0.2f, 0.5f, 0.9f};

        assertThat(AiPlaygroundService.cosineSimilarity(vector, vector)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertThat(AiPlaygroundService.cosineSimilarity(new float[]{1, 0}, new float[]{0, 1}))
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    void oppositeVectorsScoreMinusOne() {
        assertThat(AiPlaygroundService.cosineSimilarity(new float[]{1, 2}, new float[]{-1, -2}))
                .isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void magnitudeIsIgnoredBecauseOnlyDirectionCarriesMeaning() {
        assertThat(AiPlaygroundService.cosineSimilarity(new float[]{1, 2, 3}, new float[]{10, 20, 30}))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void zeroVectorScoresZeroRatherThanDividingByZero() {
        assertThat(AiPlaygroundService.cosineSimilarity(new float[]{0, 0}, new float[]{1, 1}))
                .isEqualTo(0.0);
    }

    @Test
    void mismatchedDimensionsAreRejected() {
        assertThatThrownBy(() -> AiPlaygroundService.cosineSimilarity(new float[]{1, 2}, new float[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same length");
    }
}

package com.manager.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class RateLimiterServiceTest {

    @Test
    void tokenBucketAllowsBurstThenRejectsWithoutNetworkIo() {
        RateLimiterService limiter = new RateLimiterService();

        assertTimeout(Duration.ofMillis(50), () -> {
            for (int i = 0; i < 20; i++) {
                assertThat(limiter.isRateLimited("user-1", 20)).isFalse();
            }
            assertThat(limiter.isRateLimited("user-1", 20)).isTrue();
        });
    }

    @Test
    void bucketsAreIndependentAndInvalidKeysFailClosed() {
        RateLimiterService limiter = new RateLimiterService();

        assertThat(limiter.isRateLimited("user-1", 1)).isFalse();
        assertThat(limiter.isRateLimited("user-1", 1)).isTrue();
        assertThat(limiter.isRateLimited("user-2", 1)).isFalse();
        assertThat(limiter.isRateLimited(null, 1)).isTrue();
        assertThat(limiter.trackedBucketCount()).isEqualTo(2);
    }
}

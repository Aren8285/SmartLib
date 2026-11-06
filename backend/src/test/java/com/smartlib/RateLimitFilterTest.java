package com.smartlib;

import com.smartlib.config.LoginRateLimiter;
import com.smartlib.config.RateLimitFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @Test
    void limiterAllowsTheBudgetThenReturnsRetryAfter() {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 3);

        assertThat(limiter.retryAfterSeconds("127.0.0.1")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1")).isPositive();
        // A different client keeps its own bucket.
        assertThat(limiter.retryAfterSeconds("10.0.0.9")).isZero();
    }

    @Test
    void disabledLimiterNeverBlocks() {
        LoginRateLimiter limiter = new LoginRateLimiter(false, 1);

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.retryAfterSeconds("127.0.0.1")).isZero();
        }
    }

    @Test
    void eleventhLoginAttemptGetsAJson429WithRetryAfter() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 10);
        RateLimitFilter filter = new RateLimitFilter(limiter);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest allowed = loginRequest("203.0.113.7");
            MockHttpServletResponse allowedResponse = new MockHttpServletResponse();
            filter.doFilter(allowed, allowedResponse, chain);
            assertThat(allowedResponse.getStatus()).isEqualTo(200);
        }

        MockHttpServletRequest blocked = loginRequest("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(blocked, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("Too many attempts");
        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void nonAuthGetRequestsAreNotRateLimited() throws Exception {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 1);
        RateLimitFilter filter = new RateLimitFilter(limiter);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/books");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        verify(chain, times(5)).doFilter(any(), any());
    }

    private MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }
}

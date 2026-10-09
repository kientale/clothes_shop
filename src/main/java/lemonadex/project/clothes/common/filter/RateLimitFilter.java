package lemonadex.project.clothes.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lemonadex.project.clothes.common.exception.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client limits on the endpoints that bots abuse: sign-in, sign-up, password reset, checkout and the
 * public order lookup. Counts live in memory per instance (a sliding window per client address and rule),
 * which is enough for a single server; several instances would need a shared store such as Redis.
 * Behind a reverse proxy, set server.forward-headers-strategy so the client address is the real one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
    record Rule(String name, String method, String path, int limit, Duration window) {}

    static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/api/v1/auth/login", 10, Duration.ofMinutes(1)),
            new Rule("register", "POST", "/api/v1/auth/register", 5, Duration.ofMinutes(10)),
            new Rule("forgot", "POST", "/api/v1/auth/password/forgot", 5, Duration.ofMinutes(15)),
            new Rule("reset", "POST", "/api/v1/auth/password/reset", 10, Duration.ofMinutes(15)),
            new Rule("checkout", "POST", "/api/v1/me/orders", 10, Duration.ofMinutes(10)),
            new Rule("lookup", "POST", "/api/v1/store/orders/lookup", 10, Duration.ofMinutes(5)),
            new Rule("guest-checkout", "POST", "/api/v1/store/orders", 5, Duration.ofMinutes(10)),
            new Rule("guest-pay", "POST", "/api/v1/store/orders/pay", 20, Duration.ofMinutes(10)),
            new Rule("shipping-quote", "POST", "/api/v1/store/shipping/quote", 30, Duration.ofMinutes(1)),
            new Rule("stock-alert", "POST", "/api/v1/store/stock-alerts", 10, Duration.ofMinutes(10)),
            new Rule("verify-email", "POST", "/api/v1/auth/email/verify", 10, Duration.ofMinutes(15)),
            new Rule("resend-email", "POST", "/api/v1/auth/email/resend", 5, Duration.ofMinutes(15)),
            new Rule("google", "POST", "/api/v1/auth/google", 10, Duration.ofMinutes(1)),
            new Rule("facebook", "POST", "/api/v1/auth/facebook", 10, Duration.ofMinutes(1)),
            new Rule("customer-upload", "POST", "/api/v1/me/uploads/images", 20, Duration.ofMinutes(10)),
            new Rule("vnpay-return", "POST", "/api/v1/payments/vnpay/return", 30, Duration.ofMinutes(10)),
            new Rule("momo-return", "POST", "/api/v1/payments/momo/return", 30, Duration.ofMinutes(10)));

    private final boolean enabled;
    private final ApiErrorWriter errors;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private long nextSweep;

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled, ApiErrorWriter errors, Clock clock) {
        this.enabled = enabled;
        this.errors = errors;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || rule(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rule(request);
        long now = clock.millis();
        long retryAfter = admit(rule.name() + "|" + request.getRemoteAddr(), rule, now);
        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfter / 1000)));
            errors.write(response, 429, "TOO_MANY_REQUESTS", "Too many requests; try again later");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Records the hit and returns 0, or the milliseconds until the client may try again. */
    private long admit(String key, Rule rule, long now) {
        sweep(now);
        long windowStart = now - rule.window().toMillis();
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= windowStart) times.pollFirst();
            if (times.size() >= rule.limit()) return times.peekFirst() + rule.window().toMillis() - now;
            times.addLast(now);
            return 0;
        }
    }

    /** Forgets idle clients once a minute so the map does not grow without bound. */
    private void sweep(long now) {
        if (now < nextSweep) return;
        nextSweep = now + 60_000;
        long longest = RULES.stream().mapToLong(r -> r.window().toMillis()).max().orElse(0);
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || last <= now - longest;
            }
        });
    }

    private static Rule rule(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : RULES) if (rule.method().equals(request.getMethod()) && rule.path().equals(path)) return rule;
        return null;
    }
}

package cn.minims.who.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流计桶用的地址：X-Forwarded-For 是客户端可以自由编造的头，
 * 只有在「直连对端本来就是代理」时才允许它决定桶的归属。
 */
class RateLimitInterceptorTest {

    @Test
    void usesRightmostForwardedHopForPrivateProxy() {
        assertThat(interceptor("auto").clientIp(request("172.18.0.1", "1.2.3.4, 203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void spoofedLeadingHopsCannotChangeTheBucket() {
        assertThat(interceptor("auto").clientIp(request("172.18.0.1", "192.0.2.1, 198.51.100.9")))
                .isEqualTo("198.51.100.9");
        assertThat(interceptor("auto").clientIp(request("172.18.0.1", "192.0.2.200, 198.51.100.9")))
                .isEqualTo("198.51.100.9");
    }

    @Test
    void ignoresSpoofedForwardedHeaderOnPublicConnection() {
        assertThat(interceptor("auto").clientIp(request("203.0.113.7", "198.51.100.1")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToDirectAddressWithoutForwardedHeader() {
        assertThat(interceptor("auto").clientIp(request("172.18.0.1", null))).isEqualTo("172.18.0.1");
        assertThat(interceptor("auto").clientIp(request("172.18.0.1", "  "))).isEqualTo("172.18.0.1");
    }

    @Test
    void neverStrategyUsesDirectAddressOnly() {
        assertThat(interceptor("never").clientIp(request("172.18.0.1", "203.0.113.9")))
                .isEqualTo("172.18.0.1");
    }

    @Test
    void alwaysStrategyTrustsForwardedHeaderOnPublicPeer() {
        assertThat(interceptor("always").clientIp(request("203.0.113.7", "198.51.100.1")))
                .isEqualTo("198.51.100.1");
    }

    @Test
    void treatsLoopbackAsProxied() {
        assertThat(interceptor("auto").clientIp(request("127.0.0.1", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    private static RateLimitInterceptor interceptor(String trustForwardedFor) {
        return new RateLimitInterceptor(trustForwardedFor);
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}

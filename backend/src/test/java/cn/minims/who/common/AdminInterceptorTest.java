package cn.minims.who.common;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 后台路由的两条通道：注解与 {@code /api/admin/**} 前缀，任一命中都要校验管理员角色。
 */
class AdminInterceptorTest {

    private final AdminInterceptor interceptor = new AdminInterceptor();
    private MockedStatic<StpUtil> stp;

    @AfterEach
    void closeStaticMock() {
        if (stp != null) {
            stp.close();
        }
    }

    @Test
    void unannotatedRouteUnderAdminPrefixStillRequiresAdmin() throws Exception {
        stp = mockStatic(StpUtil.class);
        MockHttpServletRequest request = request("GET", "/api/admin/anything");
        HandlerMethod handler = handler("open");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        stp.verify(() -> StpUtil.checkRole("admin"));
    }

    @Test
    void annotatedRouteOutsideAdminPrefixStillRequiresAdmin() throws Exception {
        stp = mockStatic(StpUtil.class);
        HandlerMethod handler = handler("guarded");

        interceptor.preHandle(request("GET", "/api/templates/x"), new MockHttpServletResponse(), handler);
        stp.verify(() -> StpUtil.checkRole("admin"));
    }

    @Test
    void ordinaryAuthenticatedRouteIsLeftToTheLoginGuard() throws Exception {
        stp = mockStatic(StpUtil.class);
        HandlerMethod handler = handler("open");

        assertThat(interceptor.preHandle(request("GET", "/api/resumes"), new MockHttpServletResponse(), handler))
                .isTrue();
        stp.verifyNoInteractions();
    }

    @Test
    void contextPathIsStrippedBeforeMatchingPrefix() throws Exception {
        stp = mockStatic(StpUtil.class);
        MockHttpServletRequest request = request("GET", "/miniwho/api/admin/stats");
        request.setContextPath("/miniwho");
        HandlerMethod handler = handler("open");

        interceptor.preHandle(request, new MockHttpServletResponse(), handler);
        stp.verify(() -> StpUtil.checkRole("admin"));
    }

    @Test
    void preflightIsNotRejected() throws Exception {
        stp = mockStatic(StpUtil.class);
        MockHttpServletRequest request = request("OPTIONS", "/api/admin/stats");
        HandlerMethod handler = handler("open");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        stp.verifyNoInteractions();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new ProbeController(), ProbeController.class.getMethod(methodName));
    }

    static class ProbeController {

        @RequireAdmin
        public void guarded() {
        }

        public void open() {
        }
    }
}

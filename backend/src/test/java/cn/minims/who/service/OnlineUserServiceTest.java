package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class OnlineUserServiceTest {

    @Test
    void onlineRequiresRecentActivityAndValidSession() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.isLogin(any())).thenReturn(true);
            OnlineUserService service = new OnlineUserService();
            long now = System.currentTimeMillis();
            service.touch(7L, now);
            service.touch(8L, now - 6 * 60_000L);

            assertThat(service.onlineUserIds(now)).containsExactly(7L);
            assertThat(service.onlineUserIds(now + 6 * 60_000L)).isEmpty();
        }
    }

    @Test
    void activityRestoresOnlineStatus() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.isLogin(any())).thenReturn(true);
            OnlineUserService service = new OnlineUserService();
            long now = System.currentTimeMillis();
            service.touch(7L, now - 6 * 60_000L);

            assertThat(service.onlineUserIds(now)).isEmpty();
            service.touch(7L, now);
            assertThat(service.onlineUserIds(now)).containsExactly(7L);
        }
    }

    @Test
    void logoutOrKickMarksUserOfflineImmediately() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.isLogin(any())).thenReturn(true, false);
            OnlineUserService service = new OnlineUserService();
            long now = System.currentTimeMillis();
            service.touch(7L, now);

            assertThat(service.onlineUserIds(now)).containsExactly(7L);
            assertThat(service.onlineUserIds(now)).isEmpty();
        }
    }
}

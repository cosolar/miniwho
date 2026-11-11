package cn.minims.who.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.minims.who.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UserActivityServiceTest {

    @Test
    void persistsAtMostOncePerMinutePerUser() {
        UserActivityService service = spy(new UserActivityService(mock(UserMapper.class)));
        doNothing().when(service).persist(anyLong(), anyLong());
        long now = System.currentTimeMillis();

        service.touch(7L, now);
        service.touch(7L, now + 30_000L);
        service.touch(7L, now + 61_000L);

        verify(service, times(2)).persist(anyLong(), anyLong());
    }

    @Test
    void writeFailureDoesNotBreakRequest() {
        UserMapper mapper = mock(UserMapper.class);
        doThrow(new IllegalStateException("db is locked")).when(mapper).update(any(), any());
        UserActivityService service = new UserActivityService(mapper);

        assertThatCode(() -> service.persist(7L, System.currentTimeMillis()))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsAnonymousRequests() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.isLogin()).thenReturn(false);
            UserActivityService service = spy(new UserActivityService(mock(UserMapper.class)));

            service.touchCurrentUser();

            verify(service, never()).persist(anyLong(), anyLong());
        }
    }
}

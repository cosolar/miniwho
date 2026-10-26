package cn.minims.who.service;

import cn.minims.who.common.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaTokenServiceTest {

    private final MediaTokenService service = new MediaTokenService("test-media-secret");

    @Test
    void 自己签发的链接能通过校验() {
        String token = service.issue("avatar", 42L);
        assertThatCode(() -> service.verify(token, "avatar", 42L)).doesNotThrowAnyException();
    }

    @Test
    void 作用域或简历对不上都不认() {
        String token = service.issue("avatar", 42L);
        assertThatThrownBy(() -> service.verify(token, "pdf", 42L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.verify(token, "avatar", 43L)).isInstanceOf(BizException.class);
    }

    @Test
    void 篡改正文无法通过签名校验() {
        String token = service.issue("avatar", 42L);
        int dot = token.lastIndexOf('.');
        char head = token.charAt(dot + 1);
        String forged = token.substring(0, dot + 1) + (head == 'A' ? 'B' : 'A') + token.substring(dot + 2);
        assertThatThrownBy(() -> service.verify(forged, "avatar", 42L)).isInstanceOf(BizException.class);
    }

    @Test
    void 过期链接被拒绝() {
        MediaTokenService expired = new MediaTokenService("test-media-secret", -1L);
        String token = expired.issue("avatar", 42L);
        assertThatThrownBy(() -> service.verify(token, "avatar", 42L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void 换密钥签发的链接不认() {
        String token = new MediaTokenService("another-secret").issue("avatar", 42L);
        assertThatThrownBy(() -> service.verify(token, "avatar", 42L)).isInstanceOf(BizException.class);
    }

    @Test
    void 结构残缺的链接直接判无效() {
        for (String token : new String[]{null, "", "abc", ".", "abc.", ".abc", "!!!.???"}) {
            assertThatThrownBy(() -> service.verify(token, "avatar", 1L))
                    .as("token=%s", token)
                    .isInstanceOf(BizException.class);
        }
    }
}

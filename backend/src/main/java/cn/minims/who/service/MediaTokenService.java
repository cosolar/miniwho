package cn.minims.who.service;

import cn.minims.who.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 为无 Cookie 的资源访问（分享页头像、服务端 PDF 预览）签发短时签名 URL。
 */
@Service
public class MediaTokenService {

    public static final long TTL_MILLIS = 6 * 60 * 60 * 1000L;

    private static final Logger log = LoggerFactory.getLogger(MediaTokenService.class);
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final long ttlMillis;

    @Autowired
    public MediaTokenService(@Value("${app.media-secret:}") String configuredSecret) {
        this(configuredSecret, TTL_MILLIS);
    }

    /** 有效期可注入，测试过期分支时不必真的等六小时。 */
    MediaTokenService(String configuredSecret, long ttlMillis) {
        this.ttlMillis = ttlMillis;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            this.secret = new byte[32];
            RANDOM.nextBytes(this.secret);
            log.info("未配置 MINIWHO_MEDIA_SECRET，本次启动使用随机签名密钥（重启后已下发的签名链接会失效）");
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String issue(String scope, long resumeId) {
        long expiresAt = System.currentTimeMillis() + ttlMillis;
        byte[] nonce = new byte[8];
        RANDOM.nextBytes(nonce);
        String payload = scope + ":" + resumeId + ":" + expiresAt + ":" + URL_ENCODER.encodeToString(nonce);
        return URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
                + URL_ENCODER.encodeToString(sign(payload));
    }

    public void verify(String token, String scope, long resumeId) {
        int dot = token == null ? -1 : token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw BizException.forbidden("签名链接无效");
        }
        String payload;
        byte[] provided;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            provided = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw BizException.forbidden("签名链接无效");
        }
        if (!MessageDigest.isEqual(sign(payload), provided)) {
            throw BizException.forbidden("签名链接无效");
        }
        String[] parts = payload.split(":");
        if (parts.length != 4 || !parts[0].equals(scope) || !String.valueOf(resumeId).equals(parts[1])) {
            throw BizException.forbidden("签名链接无效");
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            throw BizException.forbidden("签名链接无效");
        }
        if (expiresAt < System.currentTimeMillis()) {
            throw BizException.forbidden("签名链接已过期，请刷新页面重新获取头像链接");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("签名生成失败", e);
        }
    }
}

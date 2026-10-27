package cn.minims.who.service;

import cn.minims.who.mapper.SiteSettingMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员可调的站点开关，存在 site_settings 里，读多写少所以进内存缓存。
 * <p>库里没有这条记录时按出厂默认处理（布尔值即 false），升级部署不会因为少插了种子而行数不齐。
 */
@Service
public class SettingsService {

    /** 用户名 + 密码自助注册：无门槛，默认关闭，由管理员自行开放。 */
    public static final String REGISTER_USERNAME = "register_username";
    /** 邮箱验证码注册：还要看 SMTP 是否配置好了才算真的可用。 */
    public static final String REGISTER_EMAIL = "register_email";
    /** GitHub 登录：还要看 OAuth 凭据是否配置好了才算真的可用。 */
    public static final String GITHUB_LOGIN = "github_login";

    // 通道凭据：管理员在后台填，落同一张表；库里没这条时回落到环境变量。
    public static final String MAIL_HOST = "mail_host";
    public static final String MAIL_PORT = "mail_port";
    public static final String MAIL_USERNAME = "mail_username";
    public static final String MAIL_PASSWORD = "mail_password";
    public static final String MAIL_FROM = "mail_from";
    public static final String MAIL_FROM_NAME = "mail_from_name";
    public static final String MAIL_SSL = "mail_ssl";
    public static final String MAIL_STARTTLS = "mail_starttls";
    public static final String GITHUB_CLIENT_ID = "github_client_id";
    public static final String GITHUB_CLIENT_SECRET = "github_client_secret";
    public static final String PUBLIC_BASE_URL = "public_base_url";

    /** computeIfAbsent 不能返回 null，用空串代表「库里没有这条」。 */
    private static final String ABSENT = "";

    private final SiteSettingMapper settingMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SiteSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
    }

    public boolean enabled(String key) {
        return Boolean.parseBoolean(value(key));
    }

    public void setEnabled(String key, boolean value) {
        put(key, String.valueOf(value));
    }

    /** 库里这条配置的原文；未配置或被清空时返回 null。 */
    public String get(String key) {
        String stored = value(key);
        return stored.isBlank() ? null : stored;
    }

    /** 这项是否由后台维护（决定面板显示「后台配置」还是「环境变量」）。 */
    public boolean present(String key) {
        return get(key) != null;
    }

    /** 写入配置；传 null 或空串都表示清空，清空的项会重新回落到环境变量。 */
    public void set(String key, String value) {
        put(key, value == null ? ABSENT : value.trim());
    }

    public String or(String key, String fallback) {
        String stored = get(key);
        return stored != null ? stored : fallback;
    }

    public int orInt(String key, int fallback) {
        String stored = get(key);
        if (stored == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(stored);
        } catch (NumberFormatException malformed) {
            // 手改库改坏了也不能让发信链路抛 500，退回环境变量里的默认值。
            return fallback;
        }
    }

    public boolean orBool(String key, boolean fallback) {
        String stored = get(key);
        return stored == null ? fallback : Boolean.parseBoolean(stored);
    }

    private String value(String key) {
        return cache.computeIfAbsent(key, name -> {
            String stored = settingMapper.selectValue(name);
            return stored == null ? ABSENT : stored;
        });
    }

    private void put(String key, String value) {
        long now = System.currentTimeMillis();
        if (settingMapper.updateValue(key, value, now) == 0) {
            try {
                settingMapper.insertValue(key, value, now);
            } catch (DuplicateKeyException race) {
                // 两个管理员同时打开同一个开关时，后插的那个补一次更新即可。
                settingMapper.updateValue(key, value, now);
            }
        }
        cache.put(key, value);
    }
}

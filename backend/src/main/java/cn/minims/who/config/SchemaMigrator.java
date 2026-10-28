package cn.minims.who.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 存量库只做 ADD COLUMN，不改既有列；schema-*.sql 只负责新库首建。
 */
@Component
@Order(10)
public class SchemaMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

    private static final Map<String, Map<String, String>> TABLE_COLUMNS = new LinkedHashMap<>();

    static {
        Map<String, String> resumes = new LinkedHashMap<>();
        resumes.put("avatar_key", "VARCHAR(500)");
        resumes.put("avatar_shape", "VARCHAR(20) NOT NULL DEFAULT 'rect'");
        // 全站唯一的示例简历标记：被标上的那份会通过无鉴权接口公开显示，只有管理员能设。
        resumes.put("is_sample", "INT NOT NULL DEFAULT 0");
        TABLE_COLUMNS.put("resumes", resumes);

        // 模板包元数据：pkg 存净化后的包原文，source 区分内置与导入，二者都不能被种子互相覆盖。
        // 列类型统一按 MySQL 写法，SQLite 靠类型名里的 TEXT 取到文本亲和，不必分方言维护两套。
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("pkg", "LONGTEXT");
        templates.put("namespace", "VARCHAR(60) NOT NULL DEFAULT 'official'");
        templates.put("version", "VARCHAR(30) NOT NULL DEFAULT '1.0.0'");
        templates.put("author", "VARCHAR(100)");
        templates.put("source", "VARCHAR(20) NOT NULL DEFAULT 'builtin'");
        templates.put("api", "INT NOT NULL DEFAULT 1");
        templates.put("checksum", "VARCHAR(64)");
        templates.put("owner_user_id", "BIGINT");
        templates.put("visibility", "VARCHAR(20) NOT NULL DEFAULT 'private'");
        templates.put("exportable", "INT NOT NULL DEFAULT 1");
        TABLE_COLUMNS.put("templates", templates);

        // 第三方登录的账号也要占一行 users，password_hash 是非空列，所以用随机哈希 + 标记位区分。
        Map<String, String> users = new LinkedHashMap<>();
        users.put("has_password", "TINYINT NOT NULL DEFAULT 1");
        // 存量库里填过的邮箱没验证过，默认 0：GitHub 只关联验证码确认过的邮箱。
        users.put("email_verified", "TINYINT NOT NULL DEFAULT 0");
        // 自助注销的申请时间；存量库默认 NULL，也就是「没在注销」。
        users.put("deletion_requested_at", "BIGINT");
        // 账号头像：和简历照片分通道，存量库默认 NULL，即继续显示昵称首字母。
        users.put("avatar_key", "VARCHAR(500)");
        TABLE_COLUMNS.put("users", users);
    }

    private final DataSource dataSource;

    public SchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            boolean sqlite = "SQLite".equalsIgnoreCase(product);
            try (Statement statement = connection.createStatement()) {
                for (Map.Entry<String, Map<String, String>> table : TABLE_COLUMNS.entrySet()) {
                    Set<String> existing = sqlite
                            ? sqliteColumns(statement, table.getKey())
                            : mysqlColumns(statement, table.getKey());
                    migrate(statement, table.getKey(), table.getValue(), existing);
                }
            }
            // 邮箱从「随便填」变成登录凭据，存量库要先清掉脏数据才能上唯一索引。
            try (Statement statement = connection.createStatement()) {
                ensureEmailUniqueness(statement, sqlite);
            } catch (SQLException e) {
                log.warn("users.email 唯一索引创建失败（{}），注册与登录仍按应用层查重，不影响启动", e.getMessage());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("数据库结构升级失败，请确认数据库账号具备 ALTER 权限：" + e.getMessage(), e);
        }
    }

    private void migrate(Statement statement, String table, Map<String, String> columns, Set<String> existing)
            throws SQLException {
        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (existing.contains(column.getKey())) {
                continue;
            }
            statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column.getKey() + " " + column.getValue());
            log.info("{} 表已新增列：{}", table, column.getKey());
        }
    }

    /**
     * 邮箱改成登录凭据后必须唯一。存量库里的空串、大小写混写和重复邮箱都会挡住唯一索引，
     * 所以先规范化、再把后来者的邮箱清空（保留最早注册的账号），最后建索引。
     * 索引已经存在时直接返回，免得每次启动都全表扫一遍。
     */
    private void ensureEmailUniqueness(Statement statement, boolean sqlite) throws SQLException {
        if (emailIndexExists(statement, sqlite)) {
            return;
        }
        statement.executeUpdate("UPDATE users SET email = NULL WHERE email IS NOT NULL AND TRIM(email) = ''");
        statement.executeUpdate("UPDATE users SET email = LOWER(TRIM(email)) WHERE email IS NOT NULL");

        int collisions = 0;
        try (ResultSet rs = statement.executeQuery(
                "SELECT COUNT(*) FROM (SELECT email FROM users WHERE email IS NOT NULL"
                        + " GROUP BY email HAVING COUNT(*) > 1) dup")) {
            if (rs.next()) {
                collisions = rs.getInt(1);
            }
        }
        if (collisions > 0) {
            int cleared = statement.executeUpdate(
                    "UPDATE users SET email = NULL WHERE email IS NOT NULL AND id NOT IN ("
                            + "SELECT t.id FROM (SELECT MIN(id) AS id FROM users WHERE email IS NOT NULL GROUP BY email) t)");
            log.warn("发现 {} 组重复邮箱，已清空后注册的 {} 个账号上的邮箱，需要请本人重新绑定", collisions, cleared);
        }

        if (sqlite) {
            statement.executeUpdate("CREATE UNIQUE INDEX uk_users_email ON users(email)");
        } else {
            statement.executeUpdate("ALTER TABLE users ADD UNIQUE KEY uk_users_email (email)");
        }
        log.info("已为 users.email 建立唯一索引");
    }

    private boolean emailIndexExists(Statement statement, boolean sqlite) throws SQLException {
        String sql = sqlite
                ? "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = 'uk_users_email' LIMIT 1"
                : "SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                        + " AND TABLE_NAME = 'users' AND INDEX_NAME = 'uk_users_email' LIMIT 1";
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        }
    }

    private Set<String> sqliteColumns(Statement statement, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                names.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private Set<String> mysqlColumns(Statement statement, String table) throws SQLException {
        Set<String> names = new HashSet<>();
        try (ResultSet rs = statement.executeQuery(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "'")) {
            while (rs.next()) {
                names.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}

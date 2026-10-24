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

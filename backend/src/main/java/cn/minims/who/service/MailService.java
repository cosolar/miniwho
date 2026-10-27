package cn.minims.who.service;

import cn.minims.who.common.BizException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 标准 SMTP 发信通道，不接任何第三方发信服务。
 * <p>配置按项取值：后台填过用后台的，没填回落到环境变量，所以两种来源可以混用
 * （例如只在后台改了发件地址）。每次发信都按当前配置现建发送器，改完立刻生效，不必重启。
 */
@Slf4j
@Service
public class MailService {

    /** SMTP 对端无响应时不能把业务线程挂住，连接与读写都设硬超时。 */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    @Value("${app.mail.host:}")
    private String envHost;

    @Value("${app.mail.port:465}")
    private int envPort;

    @Value("${app.mail.username:}")
    private String envUsername;

    @Value("${app.mail.password:}")
    private String envPassword;

    @Value("${app.mail.from:}")
    private String envFrom;

    @Value("${app.mail.from-name:极简简历}")
    private String envFromName;

    @Value("${app.mail.ssl:true}")
    private boolean envSsl;

    @Value("${app.mail.starttls:false}")
    private boolean envStarttls;

    private final SettingsService settings;

    public MailService(SettingsService settings) {
        this.settings = settings;
    }

    public boolean available() {
        return notBlank(host()) && notBlank(from());
    }

    public String host() {
        return settings.or(SettingsService.MAIL_HOST, envHost);
    }

    public int port() {
        return settings.orInt(SettingsService.MAIL_PORT, envPort);
    }

    public String username() {
        return settings.or(SettingsService.MAIL_USERNAME, envUsername);
    }

    public String password() {
        return settings.or(SettingsService.MAIL_PASSWORD, envPassword);
    }

    public String from() {
        return settings.or(SettingsService.MAIL_FROM, envFrom);
    }

    public String fromName() {
        return settings.or(SettingsService.MAIL_FROM_NAME, envFromName);
    }

    public boolean ssl() {
        return settings.orBool(SettingsService.MAIL_SSL, envSsl);
    }

    public boolean starttls() {
        return settings.orBool(SettingsService.MAIL_STARTTLS, envStarttls);
    }

    /** 授权码不回显，只告诉面板「有没有」；环境变量提供也算「有」。 */
    public boolean passwordConfigured() {
        return notBlank(password());
    }

    /** 这组配置当前是不是后台维护的；false 表示值来自环境变量。 */
    public boolean configuredByAdmin() {
        return settings.present(SettingsService.MAIL_HOST);
    }

    public void send(String to, String subject, String body) {
        if (!available()) {
            throw new BizException("MAIL_NOT_CONFIGURED", "邮件服务未配置，请联系管理员",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        JavaMailSenderImpl mailSender = build();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            // 显示名会走 RFC 2047 编码，中文署名不至于在收件人那边变成乱码。
            helper.setFrom(from(), fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception e) {
            // 收件人地址不落日志（这是用户数据），失败原因只留给管理员看日志排查。
            log.error("邮件发送失败：{}", e.toString());
            throw new BizException("MAIL_SEND_FAILED", "邮件发送失败，请稍后再试或联系管理员",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private JavaMailSenderImpl build() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host());
        mailSender.setPort(port());
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 登录名缺省取发件地址：个人邮箱通常只有这一个账号，配置项也少填一项。
        String login = username();
        mailSender.setUsername(notBlank(login) ? login : from());
        mailSender.setPassword(password());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail.smtp.timeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail.smtp.writetimeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(ssl()));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls()));
        return mailSender;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

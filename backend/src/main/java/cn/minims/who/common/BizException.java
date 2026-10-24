package cn.minims.who.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Getter
public class BizException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public BizException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public BizException(String code, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public static BizException badRequest(String message) {
        return new BizException("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    public static BizException notFound(String message) {
        return new BizException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static BizException forbidden(String message) {
        return new BizException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }

    public static BizException conflict(String code, String message, Map<String, Object> details) {
        return new BizException(code, message, HttpStatus.CONFLICT, details);
    }

    public static BizException tooMany(String message) {
        return new BizException("TOO_MANY_REQUESTS", message, HttpStatus.TOO_MANY_REQUESTS);
    }
}

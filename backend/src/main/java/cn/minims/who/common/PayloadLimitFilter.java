package cn.minims.who.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.GenericFilterBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 模板包导入的请求体闸门。Jackson 在进业务代码前就把整个 JSON 读成内存树，
 * 不设上限的话一个恶意大包就能打满堆。这里读满上限还没读到结尾就 413，
 * 内存占用恒定在 maxBytes+1 以内。
 */
public class PayloadLimitFilter extends GenericFilterBean {

    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024;

    private final long maxBytes;

    public PayloadLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        if (req.getContentLengthLong() > maxBytes) {
            reject((HttpServletResponse) response);
            return;
        }
        byte[] body = req.getInputStream().readNBytes((int) maxBytes + 1);
        if (body.length > maxBytes) {
            reject((HttpServletResponse) response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(req, body), response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"模板包太大，请控制在 2MB 以内\",\"data\":null}");
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream buffer = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return buffer.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return buffer.read();
                }
            };
        }
    }
}

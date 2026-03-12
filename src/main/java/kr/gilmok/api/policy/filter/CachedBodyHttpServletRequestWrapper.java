package kr.gilmok.api.policy.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final int MAX_CACHED_BODY_BYTES = 64 * 1024;
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);

        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_CACHED_BODY_BYTES) {
            throw new IOException("Payload Too Large: Request body exceeds maximum allowed size (" + MAX_CACHED_BODY_BYTES + " bytes)");
        }

        InputStream is = request.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(contentLength >= 0 ? (int) contentLength : 512);
        byte[] buf = new byte[4096];
        int totalRead = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            totalRead += n;
            if (totalRead > MAX_CACHED_BODY_BYTES) {
                throw new IOException("Payload Too Large: Actual body size exceeds limit (" + MAX_CACHED_BODY_BYTES + " bytes)");
            }
            baos.write(buf, 0, n);
        }
        this.cachedBody = baos.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody), StandardCharsets.UTF_8));
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream stream;

        CachedBodyServletInputStream(byte[] cachedBody) {
            this.stream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return stream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async read not supported");
        }

        @Override
        public int read() throws IOException {
            return stream.read();
        }
    }
}
package cn.minims.who.util;

import cn.minims.who.common.BizException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * 图片上传通道共用的解码—缩放—重编码管线。
 *
 * <p>简历照片、条目 logo、账号头像三条通道的差异只有各自的字节上限与长边上限，
 * 其余判定完全一致，所以收在这里一份；通道自己保留错误文案，因为「头像」和「图片」
 * 在界面上要分开说。
 */
public final class ImageCodec {

    private ImageCodec() {
    }

    /** 归一化的产物：字节 + 是否带透明通道，后者决定落盘扩展名。 */
    public record Encoded(byte[] bytes, boolean alpha) {
    }

    public static boolean isJpeg(byte[] head) {
        return head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
    }

    public static boolean isPng(byte[] head) {
        return head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
    }

    /**
     * 解码并挡住过小的图：这么小的图放大到头像尺寸只会糊成一团，不如直接让用户换一张。
     *
     * @param tooSmallHint 各通道对最低像素的建议不同，文案由调用方给。
     */
    public static BufferedImage decode(byte[] raw, int minEdge, String tooSmallHint) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            throw BizException.badRequest("图片无法解析，请换一张 JPG 或 PNG");
        }
        if (image.getWidth() < minEdge || image.getHeight() < minEdge) {
            throw BizException.badRequest(tooSmallHint);
        }
        return image;
    }

    /** 长边压到 maxEdge 以内；不带透明的图统一铺白底，否则 JPEG 会把透明区渲染成黑色。 */
    public static BufferedImage prepare(BufferedImage source, boolean alpha, int maxEdge) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        int width = source.getWidth();
        int height = source.getHeight();
        if (longest > maxEdge) {
            double ratio = (double) maxEdge / longest;
            width = Math.max(1, (int) Math.round(width * ratio));
            height = Math.max(1, (int) Math.round(height * ratio));
        }
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (!alpha) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        }
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    /**
     * 是否真的用到了透明通道：颜色模型有 alpha 不等于图是透明的，
     * 截图工具导出的 PNG 几乎都带一个全不透明的 alpha，白底 JPG 更划算。
     */
    public static boolean hasAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int x = 0; x < image.getWidth(); x += Math.max(1, image.getWidth() / 24)) {
            for (int y = 0; y < image.getHeight(); y += Math.max(1, image.getHeight() / 24)) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) < 250) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 解码 → 判透明 → 缩放 → 重编码的一趟；三条通道只有长边上限不同。 */
    public static Encoded normalize(BufferedImage decoded, int maxEdge) {
        boolean alpha = hasAlpha(decoded);
        return new Encoded(encode(prepare(decoded, alpha, maxEdge), alpha), alpha);
    }

    public static byte[] encode(BufferedImage image, boolean png) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (png) {
            try {
                ImageIO.write(image, "png", out);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out.toByteArray();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JDK 缺少 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

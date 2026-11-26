import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;

/**
 * 生成手环快应用的应用图标（manifest.json 的 icon）。
 *
 * Vela 的图标签码对 PNG 相当挑剔，踩过两个坑：
 *   1. 尺寸/像素格式必须与官方 demo 一致：128x128、RGB 24 位、无 alpha
 *      （256x256 或带 alpha → 错位解码 → 花屏）
 *   2. 不能压得太狠：官方 IDAT 是 3408 字节（压缩比约 14:1）才正常。
 *      纯色大图压缩比可达 60:1 以上，解码器按「压缩长度 × 系数」预分配
 *      解压缓冲时不够用 → 同样花屏。所以这里会保证 IDAT >= 官方长度。
 *
 * 用法：
 *   java gen_band_logo.java <输出 png>                       # 默认 128，普通编码
 *   java gen_band_logo.java <输出 png> --like <模板 png>      # 与模板字节同构（推荐）
 *   java gen_band_logo.java <输出 png> 128 [--flat]          # --flat 仅供预览
 */
public class gen_band_logo {

    static final Color BG = new Color(0x12, 0x12, 0x12);
    static final Color FG = new Color(0x5D, 0xCA, 0xA5);
    static final double B = 1024.0;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "用法: java gen_band_logo.java <输出 png> [边长] [--flat] [--like <模板 png>]");
        }
        File out = new File(args[0]);
        int size = 128;
        boolean flat = false;
        File template = null;
        for (int i = 1; i < args.length; i++) {
            if ("--flat".equals(args[i])) {
                flat = true;
            } else if ("--like".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--like 需要紧跟一个模板 png 路径");
                }
                template = new File(args[++i]);
            } else {
                size = Integer.parseInt(args[i]);
            }
        }

        // 关键 1：非 flat 用 TYPE_INT_RGB，导出 24 位无 alpha，与官方一致
        int type = flat ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage img = new BufferedImage(size, size, type);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (flat) {
            mark(g, size, 0.92);
        } else {
            // 深色铺满整幅（不是白底，所以不存在“白框”），主体留 12% 边距
            g.setColor(BG);
            g.fillRect(0, 0, size, size);
            mark(g, size, 0.88);
        }
        g.dispose();

        File dir = out.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }

        if (template != null && !flat) {
            writeLikeTemplate(img, template, out);
        } else {
            ImageIO.write(img, "png", out);
            System.out.println("wrote " + out.getPath() + "  " + size + "x" + size
                    + (flat ? "  ARGB 透明底·仅主体（实验性）" : "  RGB 24位·深底"));
        }
    }

    // ---------------------------------------------------------------- 与模板同构编码

    /**
     * 输出与模板 PNG 同构的文件：IHDR 原样照抄、保留全部辅助 chunk、行过滤器策略一致，
     * 只把 IDAT 换成我们像素的压缩流，并保证 IDAT 不小于模板（规避解码器预分配缓冲不足）。
     */
    static void writeLikeTemplate(BufferedImage img, File template, File out) throws IOException {
        byte[] tpl = Files.readAllBytes(template.toPath());
        if (tpl.length < 8 || (tpl[0] & 0xFF) != 0x89 || tpl[1] != 'P') {
            throw new IllegalArgumentException("模板不是 PNG: " + template);
        }
        List<Object[]> chunks = readChunks(tpl);

        byte[] ihdr = null;
        int tplIdatLen = 0;
        List<Object[]> keep = new ArrayList<Object[]>();
        for (Object[] c : chunks) {
            String typ = (String) c[0];
            byte[] data = (byte[]) c[1];
            if ("IHDR".equals(typ)) {
                ihdr = data;
            } else if ("IDAT".equals(typ)) {
                tplIdatLen += data.length;
            } else if ("IEND".equals(typ)) {
                // 最后单独写
            } else {
                keep.add(c);
            }
        }
        if (ihdr == null) {
            throw new IllegalArgumentException("模板缺少 IHDR");
        }
        int tw = be32(ihdr, 0);
        int th = be32(ihdr, 4);
        int tbd = ihdr[8] & 0xFF;
        int tct = ihdr[9] & 0xFF;
        if (tw != img.getWidth() || th != img.getHeight() || tbd != 8 || tct != 2) {
            throw new IllegalStateException("模板规格 " + tw + "x" + th + " bd=" + tbd + " ct=" + tct
                    + " 与当前图形不匹配（需要 128x128 bd=8 ct=2）");
        }

        byte[] raw = encodeRaw(img);
        byte[] idat = deflate(raw, Deflater.DEFAULT_COMPRESSION);
        int levelUsed = Deflater.DEFAULT_COMPRESSION;
        if (idat.length < tplIdatLen) {
            // 压缩得太狠会把解码器的预分配缓冲撑爆，退到不压缩
            idat = deflate(raw, Deflater.NO_COMPRESSION);
            levelUsed = Deflater.NO_COMPRESSION;
        }

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tpl, 0, 8);                        // PNG 签名
        writeChunk(o, "IHDR", ihdr);               // 尺寸/格式与模板逐字节相同
        for (Object[] c : keep) {                  // 保留 tEXt 等辅助 chunk
            writeChunk(o, (String) c[0], (byte[]) c[1]);
        }
        writeChunk(o, "IDAT", idat);
        writeChunk(o, "IEND", new byte[0]);
        Files.write(out.toPath(), o.toByteArray());

        System.out.println("wrote " + out.getPath() + "  " + tw + "x" + th
                + "  RGB 24位·与模板同构  IDAT=" + idat.length
                + "（模板 " + tplIdatLen + "，level=" + levelUsed + "）");
    }

    /** 按模板策略编码原始扫描线：首行 Sub(1)，其余 Up(2)。 */
    static byte[] encodeRaw(BufferedImage img) throws IOException {
        int w = img.getWidth();
        int h = img.getHeight();
        int bpp = 3;
        int stride = w * bpp;
        byte[] cur = new byte[stride];
        byte[] prev = new byte[stride];
        ByteArrayOutputStream raw = new ByteArrayOutputStream(h * (stride + 1));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                cur[x * 3] = (byte) (rgb >> 16);
                cur[x * 3 + 1] = (byte) (rgb >> 8);
                cur[x * 3 + 2] = (byte) rgb;
            }
            int filter = (y == 0) ? 1 : 2;
            raw.write(filter);
            for (int i = 0; i < stride; i++) {
                int v = cur[i] & 0xFF;
                if (filter == 1) {
                    v -= (i >= bpp) ? (cur[i - bpp] & 0xFF) : 0;
                } else {
                    v -= (prev[i] & 0xFF);
                }
                raw.write(v & 0xFF);
            }
            System.arraycopy(cur, 0, prev, 0, stride);
        }
        return raw.toByteArray();
    }

    static byte[] deflate(byte[] data, int level) {
        Deflater d = new Deflater(level);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        while (!d.finished()) {
            int n = d.deflate(buf);
            o.write(buf, 0, n);
        }
        d.end();
        return o.toByteArray();
    }

    static List<Object[]> readChunks(byte[] d) {
        List<Object[]> list = new ArrayList<Object[]>();
        int i = 8;
        while (i + 12 <= d.length) {
            int ln = be32(d, i);
            String typ = new String(d, i + 4, 4, StandardCharsets.ISO_8859_1);
            byte[] data = new byte[ln];
            System.arraycopy(d, i + 8, data, 0, ln);
            list.add(new Object[]{typ, data});
            i += 12 + ln;
            if ("IEND".equals(typ)) {
                break;
            }
        }
        return list;
    }

    static void writeChunk(ByteArrayOutputStream o, String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        o.write((data.length >>> 24) & 0xFF);
        o.write((data.length >>> 16) & 0xFF);
        o.write((data.length >>> 8) & 0xFF);
        o.write(data.length & 0xFF);
        o.write(t);
        o.write(data);
        CRC32 c = new CRC32();
        c.update(t);
        c.update(data);
        long v = c.getValue();
        o.write((int) ((v >>> 24) & 0xFF));
        o.write((int) ((v >>> 16) & 0xFF));
        o.write((int) ((v >>> 8) & 0xFF));
        o.write((int) (v & 0xFF));
    }

    static int be32(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }

    // ---------------------------------------------------------------- 图形

    /** 以画布中心为基准缩放绘制，k 为图形相对画布的比例。 */
    static void mark(Graphics2D g, int size, double k) {
        double side = size * k;
        AffineTransform old = g.getTransform();
        g.translate((size - side) / 2.0, (size - side) / 2.0);
        g.scale(side / B, side / B);
        g.setColor(FG);
        g.fill(up());
        g.fill(down());
        g.fill(new Rectangle2D.Double(0.300 * B, 0.478 * B, 0.400 * B, 0.044 * B));
        g.setTransform(old);
    }

    /** 向上的箭头。 */
    static Path2D up() {
        Path2D p = new Path2D.Double();
        p.moveTo(0.500 * B, 0.150 * B);
        p.lineTo(0.645 * B, 0.302 * B);
        p.lineTo(0.5575 * B, 0.302 * B);
        p.lineTo(0.5575 * B, 0.500 * B);
        p.lineTo(0.4425 * B, 0.500 * B);
        p.lineTo(0.4425 * B, 0.302 * B);
        p.lineTo(0.355 * B, 0.302 * B);
        p.closePath();
        return p;
    }

    /** 向下的箭头。 */
    static Path2D down() {
        Path2D p = new Path2D.Double();
        p.moveTo(0.500 * B, 0.850 * B);
        p.lineTo(0.645 * B, 0.698 * B);
        p.lineTo(0.5575 * B, 0.698 * B);
        p.lineTo(0.5575 * B, 0.500 * B);
        p.lineTo(0.4425 * B, 0.500 * B);
        p.lineTo(0.4425 * B, 0.698 * B);
        p.lineTo(0.355 * B, 0.698 * B);
        p.closePath();
        return p;
    }
}

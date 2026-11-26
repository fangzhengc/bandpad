import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * 生成「腕上翻页」的启动图标：深色底 + 青绿上下箭头 + 中间一条横线（代表页面）。
 * 纯几何绘制，无外部位图依赖，任意尺寸都锐利。
 *
 * 用法：java gen_icon.java <res 目录>
 */
public class gen_icon {

    static final Color BG = new Color(0x12, 0x12, 0x12);
    static final Color FG = new Color(0x5D, 0xCA, 0xA5);
    static final double B = 1024.0;

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        String[] dirs = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};

        for (int i = 0; i < dirs.length; i++) {
            int s = sizes[i];
            File dir = new File(res, "mipmap-" + dirs[i]);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("cannot create " + dir);
            }
            ImageIO.write(legacy(s, false), "png", new File(dir, "ic_launcher.png"));
            ImageIO.write(legacy(s, true), "png", new File(dir, "ic_launcher_round.png"));
            // adaptive icon 前景是 108dp 画布
            int fg = Math.round(s * 108f / 48f);
            ImageIO.write(foreground(fg), "png", new File(dir, "ic_launcher_foreground.png"));
            System.out.println("wrote mipmap-" + dirs[i] + " size=" + s + " foreground=" + fg);
        }
        System.out.println("done");
    }

    /** 传统图标：圆角方形 / 圆形，带底色。 */
    static BufferedImage legacy(int size, boolean round) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setColor(BG);
        if (round) {
            g.fillOval(0, 0, size, size);
        } else {
            double r = size * 0.235;
            g.fill(new RoundRectangle2D.Double(0, 0, size, size, r, r));
        }
        mark(g, size, 1.0);
        g.dispose();
        return img;
    }

    /** adaptive 前景：透明底，图形收进安全区（中间 66%）。 */
    static BufferedImage foreground(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        mark(g, size, 0.86);
        g.dispose();
        return img;
    }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /** 把图形以画布中心为基准缩放绘制，k 是相对画布的比例。 */
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

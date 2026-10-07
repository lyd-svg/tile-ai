/*
 * "瓷砖还原度"的尺子 —— 把"像不像"变成一个数。
 *
 * 为什么要它
 * ----------
 * 之前只能靠肉眼说"这块砖不太像"。那样的话,每改一次提示词都只能说
 * "我看着好点了" —— **无法判断是真进步还是白折腾**。
 *
 * 做法(极简,够用就行)
 * --------------------
 * 取生成图里**贴砖的两个区域**的平均颜色,和**原砖图的平均颜色**比,
 * 得出每个通道差多少、以及一个总的色距。
 *
 * ⚠️ 这是**粗尺**,不是精密仪器。它只看颜色,不看纹理。
 *    但"瓷砖偏白还是偏灰"这种最刺眼的偏差,它足够抓得住。
 *
 * 为什么用 Java
 * -------------
 * 本项目 Python 侧没有读图库,而为一次实验改 requirements 不划算。
 * **JDK 自带 ImageIO**,PNG / JPEG 都能读 —— 零新依赖。
 * 用法:java Measure.java <图片...>
 */

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class Measure {

    /** 采样区域:按宽高的百分比给,免得写死像素。 */
    record Region(String name, double x0, double x1, double y0, double y1) {
    }

    // 生成图里:地面在下方中间,墙面在上方中间
    static final List<Region> REGIONS = List.of(
            new Region("地面", 0.30, 0.70, 0.78, 0.95),
            new Region("墙面", 0.35, 0.65, 0.10, 0.30),
            new Region("整图", 0.00, 1.00, 0.00, 1.00));

    record RGB(double r, double g, double b) {
        String hex() {
            return String.format("#%02X%02X%02X",
                    Math.round(r), Math.round(g), Math.round(b));
        }

        double dist(RGB o) {
            return Math.sqrt(Math.pow(r - o.r, 2) + Math.pow(g - o.g, 2) + Math.pow(b - o.b, 2));
        }
    }

    static RGB mean(BufferedImage img, Region reg) {
        int x0 = (int) (img.getWidth() * reg.x0());
        int x1 = (int) (img.getWidth() * reg.x1());
        int y0 = (int) (img.getHeight() * reg.y0());
        int y1 = (int) (img.getHeight() * reg.y1());
        double r = 0, g = 0, b = 0;
        long n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = img.getRGB(x, y);
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
                n++;
            }
        }
        return n == 0 ? new RGB(0, 0, 0) : new RGB(r / n, g / n, b / n);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: java Measure.java <原砖图> <生成图...>");
            return;
        }

        BufferedImage tileImg = ImageIO.read(new File(args[0]));
        if (tileImg == null) {
            System.out.println("读不了原砖图:" + args[0]);
            return;
        }
        RGB tile = mean(tileImg, REGIONS.get(2));
        System.out.printf("原砖图 %s  平均色 %s  RGB(%.0f, %.0f, %.0f)%n%n",
                args[0], tile.hex(), tile.r(), tile.g(), tile.b());

        System.out.printf("%-26s %-10s %-10s %-8s %-10s %-8s%n",
                "图片", "地面色", "色距", "墙面色", "色距", "整图色距");
        System.out.println("-".repeat(88));

        List<String> lines = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            BufferedImage img = ImageIO.read(new File(args[i]));
            if (img == null) {
                System.out.println(args[i] + "  读不了");
                continue;
            }
            RGB floor = mean(img, REGIONS.get(0));
            RGB wall = mean(img, REGIONS.get(1));
            RGB all = mean(img, REGIONS.get(2));
            String name = new File(args[i]).getName();
            System.out.printf("%-26s %-10s %-10.1f %-8s %-10.1f %-8.1f%n",
                    name, floor.hex(), floor.dist(tile), wall.hex(), wall.dist(tile), all.dist(tile));
            lines.add(name + "\t" + floor.dist(tile) + "\t" + wall.dist(tile) + "\t" + all.dist(tile));
        }

        // 分组均值:看"有文字描述"和"没文字描述"两组的差别
        System.out.println();
        System.out.println("色距越小 = 越接近原砖。⚠️ 粗尺,只看颜色不看纹理。");
    }
}

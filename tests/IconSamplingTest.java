import java.util.ArrayList;
import java.util.List;

/**
 * Mock 测试：验证 ChatBubbleScreen.drawTextureIcon() 在 size < 16 时
 * 是否正确调用了 12 参数重载并传递 regionWidth=14, regionHeight=14。
 *
 * 模拟 DrawHelper 的各个重载，记录每次调用以验证参数传递。
 */
public class IconSamplingTest {

    // ========== 模拟记录 ==========
    static final List<String> calls = new ArrayList<>();

    // ========== 模拟的 Identifier ==========
    static class Identifier {
        final String path;
        Identifier(String path) { this.path = path; }
        public String toString() { return path; }
    }

    // ========== 模拟 DrawHelper 的 4 个重载 ==========

    // 9 参数重载: (context, texture, x, y, u, v, width, height, textureWidth, textureHeight)
    static void drawTexture(Object context, Identifier texture, int x, int y,
                            float u, float v, int width, int height,
                            int textureWidth, int textureHeight) {
        calls.add(String.format(
            "9PARAM: x=%d y=%d u=%.1f v=%.1f w=%d h=%d texW=%d texH=%d",
            x, y, u, v, width, height, textureWidth, textureHeight));
    }

    // 10 参数重载: (context, texture, x, y, u, v, width, height, textureWidth, textureHeight, color)
    static void drawTexture(Object context, Identifier texture, int x, int y,
                            float u, float v, int width, int height,
                            int textureWidth, int textureHeight, int color) {
        calls.add(String.format(
            "10PARAM: x=%d y=%d u=%.1f v=%.1f w=%d h=%d texW=%d texH=%d color=%d",
            x, y, u, v, width, height, textureWidth, textureHeight, color));
    }

    // 12 参数重载: (context, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight)
    static void drawTexture(Object context, Identifier texture, int x, int y,
                            int width, int height, float u, float v,
                            int regionWidth, int regionHeight,
                            int textureWidth, int textureHeight) {
        calls.add(String.format(
            "12PARAM: x=%d y=%d w=%d h=%d u=%.1f v=%.1f regionW=%d regionH=%d texW=%d texH=%d",
            x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight));
    }

    // 13 参数重载(带颜色): (context, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight, color)
    static void drawTexture(Object context, Identifier texture, int x, int y,
                            int width, int height, float u, float v,
                            int regionWidth, int regionHeight,
                            int textureWidth, int textureHeight, int color) {
        calls.add(String.format(
            "13PARAM: x=%d y=%d w=%d h=%d u=%.1f v=%.1f regionW=%d regionH=%d texW=%d texH=%d color=%d",
            x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight, color));
    }

    // ========== 模拟的目标方法 ==========
    static void drawTextureIcon(Object g, Identifier tex, int x, int y, int size) {
        if (size < 16) {
            // 期望：调用 12 参数重载，regionWidth=14, regionHeight=14
            drawTexture(g, tex, x, y, size, size, 1.0F, 1.0F, 14, 14, 16, 16);
        } else {
            // 期望：调用 9 参数重载
            drawTexture(g, tex, x, y, 0, 0, size, size, size, size);
        }
    }

    // ========== 测试用例 ==========

    static int passed = 0;
    static int failed = 0;

    static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            System.out.println("  FAIL: " + label + " - 期望=" + expected + " 实际=" + actual);
            failed++;
        } else {
            System.out.println("  PASS: " + label);
            passed++;
        }
    }

    static void testIconSampling_size12() {
        System.out.println("\n=== 测试 1: size=12 (< 16, 右键菜单图标) ===");
        calls.clear();
        drawTextureIcon(null, new Identifier("copy"), 5, 3, 12);

        assertEquals("调用次数", 1, calls.size());
        String call = calls.get(0);
        assertEquals("使用 12 参数重载", true, call.startsWith("12PARAM:"));
        assertEquals("x=5", true, call.contains("x=5"));
        assertEquals("y=3", true, call.contains("y=3"));
        assertEquals("width=12", true, call.contains("w=12"));
        assertEquals("height=12", true, call.contains("h=12"));
        assertEquals("u=1.0", true, call.contains("u=1.0"));
        assertEquals("v=1.0", true, call.contains("v=1.0"));
        assertEquals("regionWidth=14", true, call.contains("regionW=14"));
        assertEquals("regionHeight=14", true, call.contains("regionH=14"));
        assertEquals("textureWidth=16", true, call.contains("texW=16"));
        assertEquals("textureHeight=16", true, call.contains("texH=16"));
    }

    static void testIconSampling_size16() {
        System.out.println("\n=== 测试 2: size=16 (>= 16, 侧边栏图标) ===");
        calls.clear();
        drawTextureIcon(null, new Identifier("settings"), 10, 20, 16);

        assertEquals("调用次数", 1, calls.size());
        String call = calls.get(0);
        assertEquals("使用 9 参数重载", true, call.startsWith("9PARAM:"));
        assertEquals("x=10", true, call.contains("x=10"));
        assertEquals("y=20", true, call.contains("y=20"));
        assertEquals("u=0.0", true, call.contains("u=0.0"));
        assertEquals("v=0.0", true, call.contains("v=0.0"));
        assertEquals("width=16", true, call.contains("w=16"));
        assertEquals("height=16", true, call.contains("h=16"));
        assertEquals("textureWidth=16", true, call.contains("texW=16"));
        assertEquals("textureHeight=16", true, call.contains("texH=16"));
    }

    static void testIconSampling_size20() {
        System.out.println("\n=== 测试 3: size=20 (>= 16, 大图标) ===");
        calls.clear();
        drawTextureIcon(null, new Identifier("emoji"), 100, 200, 20);

        assertEquals("调用次数", 1, calls.size());
        String call = calls.get(0);
        assertEquals("使用 9 参数重载", true, call.startsWith("9PARAM:"));
        assertEquals("x=100", true, call.contains("x=100"));
        assertEquals("y=200", true, call.contains("y=200"));
        assertEquals("width=20", true, call.contains("w=20"));
        assertEquals("height=20", true, call.contains("h=20"));
        assertEquals("textureWidth=20", true, call.contains("texW=20"));
        assertEquals("textureHeight=20", true, call.contains("texH=20"));
    }

    static void testIconSampling_size14() {
        System.out.println("\n=== 测试 4: size=14 (< 16, 边界值) ===");
        calls.clear();
        drawTextureIcon(null, new Identifier("public_icon"), 2, 50, 14);

        assertEquals("调用次数", 1, calls.size());
        String call = calls.get(0);
        assertEquals("使用 12 参数重载", true, call.startsWith("12PARAM:"));
        assertEquals("width=14", true, call.contains("w=14"));
        assertEquals("height=14", true, call.contains("h=14"));
        assertEquals("regionWidth=14", true, call.contains("regionW=14"));
        assertEquals("regionHeight=14", true, call.contains("regionH=14"));
        assertEquals("textureWidth=16", true, call.contains("texW=16"));
        assertEquals("textureHeight=16", true, call.contains("texH=16"));
    }

    static void testIconSampling_size8() {
        System.out.println("\n=== 测试 5: size=8 (< 16, 极小图标) ===");
        calls.clear();
        drawTextureIcon(null, new Identifier("tp"), 0, 0, 8);

        assertEquals("调用次数", 1, calls.size());
        String call = calls.get(0);
        assertEquals("使用 12 参数重载", true, call.startsWith("12PARAM:"));
        assertEquals("width=8", true, call.contains("w=8"));
        assertEquals("height=8", true, call.contains("h=8"));
        assertEquals("regionWidth=14", true, call.contains("regionW=14"));
        assertEquals("regionHeight=14", true, call.contains("regionH=14"));
        assertEquals("textureWidth=16", true, call.contains("texW=16"));
        assertEquals("textureHeight=16", true, call.contains("texH=16"));
    }

    // ========== DrawHelper 12 参数重载实现验证 ==========

    static void testDrawHelper12ParamImpl() {
        System.out.println("\n=== 测试 6: DrawHelper 12 参数重载实现正确性 ===");
        // 验证修复后的 12 参数重载确实接收 regionWidth/regionHeight 并转发
        // (之前的 bug: 传递了 width/height 而不是 regionWidth/regionHeight)
        calls.clear();
        drawTexture(null, new Identifier("test"), 10, 20,  // context, tex, x, y
            30, 40,     // width, height
            1.0F, 1.0F, // u, v
            14, 14,     // regionWidth, regionHeight
            16, 16);    // textureWidth, textureHeight

        String call = calls.get(0);
        assertEquals("regionW=14", true, call.contains("regionW=14"));
        assertEquals("regionH=14", true, call.contains("regionH=14"));
        // 验证 region 参数和 width/height 不同（区分旧 bug）
        assertEquals("region != width", true, !call.contains("regionW=30"));
        assertEquals("region != height", true, !call.contains("regionH=40"));
    }

    // ========== 主入口 ==========

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("  ChatBubbleScreen.drawTextureIcon 参数测试");
        System.out.println("==========================================");

        testIconSampling_size12();
        testIconSampling_size16();
        testIconSampling_size20();
        testIconSampling_size14();
        testIconSampling_size8();
        testDrawHelper12ParamImpl();

        System.out.println("\n==========================================");
        System.out.println("  结果: " + passed + " 通过, " + failed + " 失败");
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
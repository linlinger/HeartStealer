package linlinger.heartstealer.client.hud;

import linlinger.heartstealer.attribute.PlayerAttributeManager;
import linlinger.heartstealer.client.mixin.GuiSpritesAccessor;
import linlinger.heartstealer.config.HeartStealerConfig;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端 HUD:加成信息文字 + (可选)自定义红条 / 装甲栏。
 *
 * <p><b>两种渲染模式(运行时自动切换):</b></p>
 * <ol>
 *   <li><b>纯文字模式</b>:血条渲染交给别人(装了「彩色心心 colorfulhearts」
 *       或配置 customHealthBar 关闭)。此时本模组<b>不隐藏原版红心/装甲栏</b>,
 *       只画两行加成文字,位置固定在原版装甲栏上方。</li>
 *   <li><b>自定义模式</b>:没装 colorfulhearts 且配置开启。此时原版红心与
 *       装甲栏被 Mixin 隐藏(GuiHeartsMixin / GuiArmorMixin),这里自绘
 *       精致红条 + 原版样式装甲栏 + 文字。</li>
 * </ol>
 *
 * <p><b>文字布局(自下而上,左边缘与原版红心对齐 w/2-91):</b></p>
 * <pre>
 *  攻击 +15            ← 第 1 行文字:攻击力加成
 *  生命 520            ← 第 2 行文字:当前最大生命
 *  [装甲栏]            ← 原版装甲栏(纯文字模式)或自绘装甲栏(自定义模式)
 *  [红心/红条]          ← colorfulhearts / 原版 / 本模组红条
 * </pre>
 *
 * <p><b>状态变色(仅自定义模式):</b>中毒 → 血条变绿;凋零 → 变暗黑;
 * 冰冻 → 变蓝;正常 → 红色(参考原版红心会随状态改变颜色)。</p>
 *
 * <p>只使用 fill / drawString / blitSprite,不注册渲染层。</p>
 */
public final class HeartStealerHud {

    // ==================== 红条 ====================
    /** 红条宽度(精致小巧) */
    private static final int BAR_WIDTH = 60;
    /** 红条高度(与文字高度接近,让文字能"嵌"在内部) */
    private static final int BAR_HEIGHT = 8;
    /** 红条左边缘距屏幕中线的偏移(与原版红心第一排起始位置一致) */
    private static final int BAR_X_OFFSET = 91;
    /** 红条上边缘距屏幕底部的距离(与原版红心第一排位置一致) */
    private static final int BAR_Y_FROM_BOTTOM = 39;
    /** 装甲栏上边缘距屏幕底部的距离(红条上方 10px,与原版装甲栏位置一致) */
    private static final int ARMOR_Y_FROM_BOTTOM = 49;
    /** 装甲图标大小(与原版一致) */
    private static final int ARMOR_ICON_SIZE = 9;
    /** 相邻装甲图标水平间距(与原版一致) */
    private static final int ARMOR_ICON_STEP = 8;
    /** 装甲图标数量上限 */
    private static final int ARMOR_ICON_COUNT = 10;

    // ==================== 文字 ====================
    private static final int TEXT_GAP = 4;       // 文字与装甲栏之间的间距
    private static final int LINE_HEIGHT = 9;    // 文字行高
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    /** 外部血条渲染 mod 的 mod id(彩色心心,负责替换原版红心) */
    private static final String COLORFUL_HEARTS_MOD_ID = "colorfulhearts";

    // ==================== 颜色(按状态切换,仅自定义模式用) ====================
    private static final int BAR_BG_COLOR = 0xFF141414;        // 空血底色
    private static final int BAR_BORDER_COLOR = 0xFF1A1A1A;    // 边框
    // 正常:红
    private static final int FILL_RED = 0xFFFF4444;
    private static final int HIGHLIGHT_RED = 0xFFFF7777;
    // 中毒:绿(参考原版中毒红心)
    private static final int FILL_POISON = 0xFF55FF55;
    private static final int HIGHLIGHT_POISON = 0xFF77FF77;
    // 凋零:暗黑(参考原版凋零红心)
    private static final int FILL_WITHER = 0xFF3F3F3F;
    private static final int HIGHLIGHT_WITHER = 0xFF555555;
    // 冰冻:蓝(参考原版冰冻红心)
    private static final int FILL_FROZEN = 0xFF55AAFF;
    private static final int HIGHLIGHT_FROZEN = 0xFF77BBFF;

    /** 工具类,禁止实例化 */
    private HeartStealerHud() {
    }

    /**
     * 是否使用本模组自绘的红条 + 装甲栏渲染。
     *
     * <p>返回 {@code true} = 隐藏原版红心/装甲栏,由本模组自绘;
     * 返回 {@code false} = 不隐藏原版,只画加成文字。</p>
     *
     * <p>规则:</p>
     * <ol>
     *   <li>装了 colorfulhearts(它负责渲染血条)→ 不用本模组的渲染;</li>
     *   <li>配置 customHealthBar = false → 不用本模组的渲染;</li>
     *   <li>否则 → 用本模组的渲染。</li>
     * </ol>
     *
     * <p>这个静态方法也被 GuiHeartsMixin / GuiArmorMixin 调用,
     * 用来决定是否取消原版红心/装甲栏,保证两种模式一致。</p>
     */
    public static boolean shouldUseCustomRender() {
        // 彩色心心已加载:它的血条渲染优先,我们不抢
        if (FabricLoader.getInstance().isModLoaded(COLORFUL_HEARTS_MOD_ID)) {
            return false;
        }
        // 否则看配置:开启自定义渲染才用,关闭则退回纯文字模式(代码保留)
        HeartStealerConfig config =
                AutoConfig.getConfigHolder(HeartStealerConfig.class).getConfig();
        return config.customHealthBar;
    }

    /**
     * 每一帧被 {@code HudRenderCallback} 回调,绘制文字、装甲栏和红条。
     *
     * @param guiGraphics 当前帧的绘制上下文
     * @param tickCounter 渲染帧计时器(本实现用不到,仅满足监听器签名)
     */
    public static void render(GuiGraphics guiGraphics, DeltaTracker tickCounter) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return; // 主菜单/加载中不画
        }
        Font font = minecraft.font;

        int windowWidth = minecraft.getWindow().getGuiScaledWidth();
        int windowHeight = minecraft.getWindow().getGuiScaledHeight();

        // ---------- 1. 数值 ----------
        int attackBonus = (int) PlayerAttributeManager.getAttackBonus(player);
        int maxHealth = (int) Math.ceil(player.getMaxHealth());

        // ---------- 2. 位置(左边缘 = 屏幕中线 - 91,和原版一致) ----------
        int barX = windowWidth / 2 - BAR_X_OFFSET;
        int armorY = windowHeight - ARMOR_Y_FROM_BOTTOM;  // 装甲栏顶
        int line2Y = armorY - LINE_HEIGHT - TEXT_GAP;     // 生命 N
        int line1Y = line2Y - LINE_HEIGHT;                // 攻击 +N

        // ---------- 3. 两行文字(任何模式都画,在原版装甲栏上方) ----------
        guiGraphics.drawString(font, Component.literal("攻击 +" + attackBonus),
                barX, line1Y, TEXT_COLOR, true);
        guiGraphics.drawString(font, Component.literal("生命 " + maxHealth),
                barX, line2Y, TEXT_COLOR, true);

        // ---------- 4. 不用本模组的渲染(装了 colorfulhearts 或配置关闭) → 到此为止 ----------
        // 原版红心/装甲栏没有被 Mixin 隐藏,正常显示;文字已经画好,直接返回。
        if (!shouldUseCustomRender()) {
            return;
        }

        // ---------- 5. 以下是自定义渲染(红条 + 装甲栏) ----------
        int currentHealth = (int) Math.ceil(player.getHealth());
        int barY = windowHeight - BAR_Y_FROM_BOTTOM;      // 红条顶

        // 5.1 装甲栏(原版样式图标)
        drawArmor(guiGraphics, player, barX, armorY);

        // 5.2 红条(状态变色)
        int fillColor = FILL_RED;
        int highlightColor = HIGHLIGHT_RED;
        if (player.hasEffect(MobEffects.WITHER)) {
            fillColor = FILL_WITHER;       // 凋零:暗黑
            highlightColor = HIGHLIGHT_WITHER;
        } else if (player.hasEffect(MobEffects.POISON)) {
            fillColor = FILL_POISON;       // 中毒:绿
            highlightColor = HIGHLIGHT_POISON;
        } else if (player.getTicksFrozen() > 0) {
            fillColor = FILL_FROZEN;       // 冰冻:蓝
            highlightColor = HIGHLIGHT_FROZEN;
        }

        // 深色底(空血部分)
        guiGraphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, BAR_BG_COLOR);
        // 红色填充:宽度按 当前/最大 比例,并钳制到 [0, BAR_WIDTH] 防止超出(遮盖饱食度)
        int fillWidth = maxHealth > 0
                ? (int) ((long) BAR_WIDTH * Math.min(currentHealth, maxHealth) / maxHealth)
                : 0;
        fillWidth = Math.min(fillWidth, BAR_WIDTH);
        if (fillWidth > 0) {
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, fillColor);
            guiGraphics.fill(barX, barY, barX + fillWidth, barY + 1, highlightColor); // 顶部高光
        }
        // 近黑细边框
        guiGraphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY, BAR_BORDER_COLOR);
        guiGraphics.fill(barX - 1, barY + BAR_HEIGHT, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, BAR_BORDER_COLOR);
        guiGraphics.fill(barX - 1, barY - 1, barX, barY + BAR_HEIGHT + 1, BAR_BORDER_COLOR);
        guiGraphics.fill(barX + BAR_WIDTH, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, BAR_BORDER_COLOR);

        // ---------- 5.3 血量文字 "当前/最大" 内嵌居中 ----------
        Component barText = Component.literal(currentHealth + "/" + maxHealth);
        int textWidth = font.width(barText);
        guiGraphics.drawString(font, barText,
                barX + (BAR_WIDTH - textWidth) / 2, barY, TEXT_COLOR, true);
    }

    /**
     * 画原版样式的装甲栏:根据玩家的护甲值,依次画 10 个满/半/空图标。
     *
     * @param guiGraphics 绘制上下文
     * @param player      玩家
     * @param startX      左边缘(与红条对齐)
     * @param y           装甲栏顶边(红条上方 10px)
     */
    private static void drawArmor(GuiGraphics guiGraphics, Player player, int startX, int y) {
        int armor = player.getArmorValue(); // 护甲值(如满铁甲 = 20)
        for (int i = 0; i < ARMOR_ICON_COUNT; i++) {
            int x = startX + i * ARMOR_ICON_STEP;
            Identifier sprite;
            if (armor >= 2) {
                sprite = GuiSpritesAccessor.getArmorFullSprite(); // 满格
            } else if (armor == 1) {
                sprite = GuiSpritesAccessor.getArmorHalfSprite(); // 半格
            } else {
                sprite = GuiSpritesAccessor.getArmorEmptySprite(); // 空槽
            }
            // 用原版同样的贴图和渲染管线画图标,样式与原版完全一致
            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    sprite, x, y, ARMOR_ICON_SIZE, ARMOR_ICON_SIZE);
            armor -= 2;
        }
    }
}

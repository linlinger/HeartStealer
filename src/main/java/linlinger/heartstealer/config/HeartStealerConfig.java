package linlinger.heartstealer.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * HeartStealer(夺心者)的全部配置项。
 *
 * <p>使用 Cloth Config 的 AutoConfig 自动注册,首次运行会自动生成配置文件:
 * {@code config/heartstealer.json}。</p>
 *
 * <p><b>如何读取配置:</b>在代码任意位置用下面这一行拿到当前配置对象:</p>
 * <pre>
 *   HeartStealerConfig config =
 *       AutoConfig.getConfigHolder(HeartStealerConfig.class).getConfig();
 * </pre>
 * 或者在主类 Heartstealer 里调用 {@code Heartstealer.getConfig()}。
 *
 * <p>字段名就是 json 里的键名;字段上的中文注释会显示在 ModMenu 配置界面的提示里。</p>
 */
@Config(name = "heartstealer")
public class HeartStealerConfig implements ConfigData {

    // ================================================================
    // 一、常规设置
    // ================================================================

    /**
     * 开局生命上限。
     * Minecraft 里 1 点生命 = 半颗心,所以默认 1.0 就是"半颗心开局"。
     */
    @ConfigEntry.Gui.Tooltip
    public double initialHealth = 1.0;

    /**
     * 每次击杀生物增加多少攻击力。
     * 该加成是"基础攻击力"级别的 modifier,空手和手持武器都能享受。
     */
    @ConfigEntry.Gui.Tooltip
    public double attackDamagePerKill = 1.0;

    /**
     * 死亡后是否保留全部加成。
     * true = 死亡无惩罚,重生后永久保留(本项目需求);false = 死亡清空。
     */
    @ConfigEntry.Gui.Tooltip
    public boolean keepOnDeath = true;

    /**
     * 生命上限的最大值(即"最大生命"属性允许达到的上限)。
     * 默认 1000000 = 基本无上限(原版写死 1024,本模组已绕过)。
     * 想设上限就改成具体数值,如 2048、50000。
     * 联机时以主机配置为准(会同步给客户机)。
     */
    @ConfigEntry.Gui.Tooltip
    public double maxHealthCap = 1000000.0;

    // ================================================================
    // 二、击杀范围
    // ================================================================

    /** 被动生物(猪、牛、羊、兔子等)击杀是否计入奖励 */
    public boolean includePassive = true;

    /** 敌对生物(僵尸、骷髅、苦力怕等)击杀是否计入奖励 */
    public boolean includeHostile = true;

    /** Boss 生物(凋灵、末影龙等)击杀是否计入奖励 */
    public boolean includeBoss = true;

    /** PvP(击杀其他玩家)是否计入奖励(默认关闭:PVP 不带来血量加成) */
    public boolean includePlayers = false;

    /**
     * PvP 伤害加成开关(默认关闭)。
     * <p>开启后:你的<b>远程/投掷物</b>(箭、三叉戟、雪球、药水等)打其他玩家时,
     * 伤害也会加上攻击加成;关闭(默认)时,打其他玩家保持原版伤害,不加任何加成。</p>
     * <p>注意:该开关只控制"玩家被打时"是否吃加成;打怪(非玩家)始终吃加成。
     * 另外近战因走原版 attack_damage 属性,打玩家仍会带属性加成(机制决定,无法按目标区分)。</p>
     */
    @ConfigEntry.Gui.Tooltip
    public boolean pvpDamageBonus = false;

    /**
     * Boss 生命加成倍率。
     * 1.0 = 全额(Boss 200 血就 +200 生命上限);
     * 0.5 = 减半(Boss 200 血只 +100),用于平衡后期成长过快。
     */
    @ConfigEntry.Gui.Tooltip
    public double bossMultiplier = 1.0;

    /**
     * 黑名单:不触发奖励的实体 ID。
     * 默认已把盔甲架 {@code minecraft:armor_stand} 加进去(它默认被当成"被动生物",
     * 击杀会白得 +20 生命,多数情况不是玩家想要的);
     * 可以继续追加,例如 {"minecraft:armor_stand", "minecraft:villager"}。
     * 注意:已经生成过配置文件的老存档,需要自己手动在黑名单里加上 armor_stand。
     */
    public List<String> entityBlacklist = new ArrayList<>(List.of("minecraft:armor_stand"));

    // ================================================================
    // 三、HUD 与血条显示
    // ================================================================

    /**
     * 是否使用本模组自绘的红条 + 装甲栏渲染。
     * <p>规则(运行时自动判断):</p>
     * <ul>
     *   <li>只要装了「彩色心心 colorfulhearts」mod,血条渲染就<b>交给它</b>,
     *       本模组只负责在装甲栏上方显示加成文字(此时本开关被自动忽略);</li>
     *   <li>没装 colorfulhearts 时,本开关才生效:
     *       true = 用本模组的红条 + 装甲栏渲染;false = 完全禁用本模组的
     *       血条渲染(原版红心照常显示),自绘代码保留备用。</li>
     * </ul>
     */
    @ConfigEntry.Gui.Tooltip
    public boolean customHealthBar = true;

    /**
     * 血条显示模式:
     * <ul>
     *   <li>{@link HealthBarMode#DNF} —— 多层彩色(参考 DNF,每层颜色不同)</li>
     *   <li>{@link HealthBarMode#NUMERIC} —— 数字补偿(原版血条 + 溢出数字 +N)</li>
     * </ul>
     */
    public HealthBarMode healthBarMode = HealthBarMode.DNF;

    /**
     * DNF 模式下最多渲染多少层。1 层 = 3 排血条 = 60 颗心。
     * 超过该层数的部分用 "+N" 数字表示,防止血条铺满整个屏幕。
     */
    public int maxHealthLayers = 5;

    /** 每一"管"血条的颜色,顺序对应第 1 管、第 2 管……(默认红/金/绿/蓝/紫) */
    public List<String> layerColors = new ArrayList<>(List.of("red", "gold", "green", "blue", "purple"));

    /** 是否在屏幕角落显示攻击力加成 HUD */
    public boolean showAttackHud = true;

    /** 攻击力 HUD 显示在屏幕哪个角落 */
    public HudPosition hudPosition = HudPosition.TOP_RIGHT;

    // ================================================================
    // 枚举定义
    // ================================================================

    /** 血条显示模式 */
    public enum HealthBarMode {
        /** 多层彩色(参考 DNF) */
        DNF,
        /** 数字补偿:原版血条 + 溢出 +N */
        NUMERIC
    }

    /** HUD 显示位置 */
    public enum HudPosition {
        /** 左上角 */
        TOP_LEFT,
        /** 右上角(默认) */
        TOP_RIGHT,
        /** 左下角 */
        BOTTOM_LEFT,
        /** 右下角 */
        BOTTOM_RIGHT
    }
}

package linlinger.heartstealer.mixin;

import linlinger.heartstealer.Heartstealer;
import linlinger.heartstealer.config.HeartStealerConfig;
import linlinger.heartstealer.config.SyncedConfig;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 绕过原版属性的硬上限:最大生命 1024、攻击力 2048。
 *
 * <p>原版的钳制逻辑在 {@link RangedAttribute#sanitizeValue(double)} 里
 * (不是基类 {@link Attribute} 的 sanitizeValue —— 那个被 RangedAttribute 重写了)。</p>
 *
 * <ul>
 *   <li>MAX_HEALTH 注册为 RangedAttribute("attribute.name.max_health", 20.0, 1.0, 1024.0),
 *       上限 1024 就存在这个对象的 maxValue 里;</li>
 *   <li>ATTACK_DAMAGE 注册为 RangedAttribute("attribute.name.attack_damage", 1.0, 0.0, 2048.0),
 *       上限 2048(已从 Attributes 字节码确认)。</li>
 * </ul>
 *
 * <p>这里拦截 {@code RangedAttribute.sanitizeValue}:</p>
 * <ul>
 *   <li>「最大生命」→ 用配置 {@code maxHealthCap} 代替 1024 作为上限(可配置、联机以主机为准);</li>
 *   <li>「攻击力」→ 直接放开上限(只保下限),满足"攻击力无上限"的需求,
 *       否则近战加成累计超过 2048 后会被原版钳死、不再增长,而远程/投掷物
 *       (直接加伤害值)不受钳制,导致后期两者数值不一致;</li>
 *   <li>其它属性一律不管(保持原版行为)。</li>
 * </ul>
 *
 * <p><b>注意(已知设计局限)</b>:属性不知道"自己属于哪个实体",所以这两个上限放开
 * 对<b>所有</b>持有该属性的实体(玩家 + 生物)都生效。默认 maxHealthCap = 1000000
 * 对普通生物无影响;只有把 cap 手动调得很小(低于生物血量)时,生物的血量上限才会
 * 一并被限制 —— 这是可接受的行为(小 cap 本来就是整体平衡意图)。</p>
 */
@Mixin(RangedAttribute.class)
public abstract class AttributeCapMixin {

    /** 最大生命属性的 descriptionId(已从 Attributes 字节码确认,注意没有 generic. 前缀) */
    private static final String MAX_HEALTH_ID = "attribute.name.max_health";

    /** 攻击力属性的 descriptionId(同样没有 generic. 前缀) */
    private static final String ATTACK_DAMAGE_ID = "attribute.name.attack_damage";

    /** 本类专用日志,便于在日志里看到 Mixin 是否生效 */
    private static final Logger LOGGER = LoggerFactory.getLogger("HeartStealerCap");

    /** 启动时是否已打过"已接管"日志 */
    private static boolean loggedOnce = false;

    /**
     * 拦截"最大生命"和"攻击力"两个属性的数值钳制。
     *
     * @param value 要被钳制的数值(新的 base 值或计算值)
     * @param cir   回调:用 setReturnValue 返回我们算好的钳制结果
     */
    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void heartstealer$applyCustomCap(double value, CallbackInfoReturnable<Double> cir) {
        // 1) 按 descriptionId 区分属性(用 descriptionId 判断,比引用比较稳;
        //    注意:Mixin 类编译时看不到目标类方法,要强转 (Attribute)(Object)this 调用)
        String attributeId = ((Attribute) (Object) this).getDescriptionId();
        if (MAX_HEALTH_ID.equals(attributeId)) {
            // ---------- 最大生命:用配置上限代替原版 1024 ----------
            // 读取上限:优先用主机同步来的值(联机以主机配置为准);
            // 没收到同步时用本地配置;配置还没初始化时用 1000000 兜底(≈无上限)。
            HeartStealerConfig config = Heartstealer.getConfig();
            double cap = (config != null) ? config.maxHealthCap : 1000000.0;
            if (SyncedConfig.maxHealthCap > 0) {
                cap = SyncedConfig.maxHealthCap; // 主机同步值优先
            }
            // 钳制到 [minValue, cap]:下限保持原版(MAX_HEALTH 的 min 是 1.0)
            double min = ((RangedAttribute) (Object) this).getMinValue();
            cir.setReturnValue(Math.max(min, Math.min(value, cap)));
        } else if (ATTACK_DAMAGE_ID.equals(attributeId)) {
            // ---------- 攻击力:放开上限(原版 2048),只保下限 ----------
            // 让"攻击力无上限"成立,近战与远程/投掷物的加成保持一致增长。
            double min = ((RangedAttribute) (Object) this).getMinValue();
            cir.setReturnValue(Math.max(min, value));
        } else {
            return; // 其它属性:保持原版行为
        }

        // 2) 启动时打一次日志,确认 Mixin 生效(只打一次,避免刷屏)
        if (!loggedOnce) {
            loggedOnce = true;
            LOGGER.info("[HeartStealer] 属性上限已接管:最大生命按 maxHealthCap 配置,攻击力无上限(原版 1024/2048 已绕过)");
        }
    }
}

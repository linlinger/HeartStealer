package linlinger.heartstealer.mixin;

import linlinger.heartstealer.Heartstealer;
import linlinger.heartstealer.attribute.PlayerAttributeManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 远程 / 投掷物伤害加成 Mixin。
 *
 * <p><b>需求</b>:玩家的<b>所有</b>武器伤害都要加上攻击力加成 —— 近战、远程(弓/弩/三叉戟投掷)、
 * 投掷物(雪球/鸡蛋/喷溅药水)统统生效;<b>唯独 TNT、重生锚、末地放床的爆炸不算</b>。</p>
 *
 * <p><b>为什么用 {@code @Redirect} 重定向 {@code actuallyHurt}:</b></p>
 * <ul>
 *   <li>近战伤害已经通过 {@link net.minecraft.world.entity.ai.attributes.Attributes#ATTACK_DAMAGE}
 *       属性修饰符生效(见 {@code PlayerAttributeManager}),本 mixin 不再重复加,避免双倍;</li>
 *   <li>远程和投掷物在 Minecraft 里都实现为<b>弹射物</b>({@link Projectile}),且都记录着
 *       <b>投出它的玩家</b>({@link Projectile#getOwner()})。所以只要在伤害结算时判断
 *       "这次伤害是玩家拥有的弹射物造成的",就把攻击加成加进伤害值;</li>
 *   <li>1.21.11 里护甲/魔抗减免发生在 {@code actuallyHurt} 内部,所以在
 *       {@code hurtServer} 调用 {@code actuallyHurt} 时把加成加到传入的伤害值上,
 *       加成会和近战属性一样<b>正常吃护甲</b>;</li>
 *   <li><b>TNT / 重生锚 / 末地床爆炸天然被排除</b>:它们的伤害来源实体不是弹射物、
 *       也没有"玩家主人"这一概念,下面的判断不会命中,无需任何特殊处理。</li>
 * </ul>
 *
 * <p><b>1.21.11 映射坑(已踩过)</b>:</p>
 * <ul>
 *   <li>{@code hurtServer} 签名是 {@code (ServerLevel, DamageSource, float)},
 *       比旧版本多了一个 {@code ServerLevel} 参数;</li>
 *   <li>{@code Projectile} 已移到 {@code net.minecraft.world.entity.projectile.Projectile}
 *       (不再是 {@code world.entity.Projectile});</li>
 *   <li>{@code actuallyHurt} 在 {@code hurtServer} 里被调用两次(if/else 互斥分支:无敌帧内
 *       只结算超出部分 / 正常结算)。两个调用点都重定向,但运行时只执行其中一个分支,
 *       所以加成只会加一次;</li>
 *   <li>用 {@code @Redirect} + {@code @Shadow}:handler 签名是标准的
 *       {@code (目标对象, 目标方法参数...)},无歧义;{@code @Shadow} 把 protected 的
 *       {@code actuallyHurt} 声明出来供 handler 调用。</li>
 * </ul>
 *
 * <p><b>安全性</b>:require 用默认值,若某版本方法改名/改签名导致注入失败,
 * 会<b>明确报错</b>而不是静默跳过 —— 宁可启动时报错,也不要"看着生效其实没生效"。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    /**
     * 声明目标类的 protected 方法 {@code actuallyHurt},供下方 {@code @Redirect} 的
     * handler 里调用(相当于"绕过 protected 权限访问原方法")。
     */
    @Shadow
    protected abstract void actuallyHurt(ServerLevel level, DamageSource source, float amount);

    /**
     * 把 {@code hurtServer} 内部对 {@code actuallyHurt} 的调用重定向到这里:
     * 判定"这次伤害是玩家拥有的弹射物造成的",是则给伤害值加上攻击加成,再调用原方法。
     *
     * @param target 受伤的实体(原方法所属对象,即 this)
     * @param level  服务端世界
     * @param source 伤害来源(可取出"直接造成伤害的实体")
     * @param amount 本次结算的原始伤害值
     */
    @Redirect(method = "hurtServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/damagesource/DamageSource;F)V"))
    private void heartstealer$addProjectileDamage(
            LivingEntity target, ServerLevel level, DamageSource source, float amount) {

        float finalAmount = amount;

        // ---------- 1. 取出"直接造成伤害的实体" ----------
        // 远程/投掷物伤害:直接实体 = 弹射物本体(箭/雪球/药水/三叉戟…);
        // 近战伤害:直接实体 = 玩家本人;TNT/床/锚爆炸:直接实体 = TNT/空。
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof Player attacker) {
            // ---------- 2. PvP 伤害加成开关:打玩家默认不加(保持原版伤害) ----------
            // 打怪(非玩家)始终加加成;打玩家时看配置 pvpDamageBonus(默认 false)。
            boolean addBonus = true;
            if (target instanceof Player) {
                addBonus = Heartstealer.getConfig().pvpDamageBonus;
            }
            if (addBonus) {
                // ---------- 3. 命中:玩家投出的弹射物打在"该加成的目标"上,加上攻击加成 ----------
                double bonus = PlayerAttributeManager.getAttackBonus(attacker);
                if (bonus > 0) {
                    finalAmount = amount + (float) bonus;
                    // 调试日志(debug 级,不刷屏):确认加成是否真的作用到远程/投掷物伤害上
                    // 需要排查时可在开发环境把日志级别调到 DEBUG 查看。
                    Heartstealer.LOGGER.debug("[HeartStealer] 弹射物伤害加成 +{} (原 {},目标 {})",
                            bonus, amount, target.getType().getDescriptionId());
                }
            }
        }

        // ---------- 3. 调用被重定向的原方法(用加成后的伤害) ----------
        // 护甲/魔抗减免在 actuallyHurt 内部进行,所以加成和近战一样正常吃护甲。
        // 通过"强转成 mixin 类型"来调用 @Shadow 声明的 protected 方法(绕过访问限制)。
        ((LivingEntityHurtMixin) (Object) target).actuallyHurt(level, source, finalAmount);
    }
}

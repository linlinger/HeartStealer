package linlinger.heartstealer.attribute;

import linlinger.heartstealer.Heartstealer;
import linlinger.heartstealer.config.HeartStealerConfig;

import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家属性管理器 —— 纯静态工具类,负责读写玩家的"生命上限"与"攻击力加成"两项属性。
 *
 * <p>核心思路:玩家的生命上限通过直接修改 {@link Attributes#MAX_HEALTH} 的 base 值实现
 * (每次击杀都会永久累加,且被 Minecraft 原版自动写进玩家 NBT 存档);
 * 攻击力加成则通过一个带固定 ID 的"永久属性修饰符"(AttributeModifier)实现,
 * 每次击杀把旧的移除、换上更大的新值,同样会自动持久化。</p>
 *
 * <p>这里只用基础属性与修饰符,不碰装备/药水等临时加成,保证加成稳定可复制。</p>
 */
public final class PlayerAttributeManager {

    /**
     * 攻击力加成的修饰符 ID(全局唯一)。
     * 使用固定 ID 是为了能随时从属性里查到"这个加成到底有多大",方便累加与重生复制。
     */
    public static final Identifier ATTACK_DAMAGE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("heartstealer", "attack_bonus");

    /** 工具类禁止实例化 */
    private PlayerAttributeManager() {
    }

    /**
     * 玩家首次进服时的初始化:把生命上限从原版默认的 20.0 改为配置的 initialHealth(半颗心)。
     *
     * <p>为什么要判断"当前 base 仍是 20.0"才改?因为老玩家进服时 base 可能早已被之前的
     * 击杀加成改大了,不能再重置回半颗心;只有从没被本模组动过(还停在原版默认 20.0)
     * 的玩家才需要初始化。</p>
     *
     * @param player 进服的玩家
     */
    public static void initPlayerHealth(Player player) {
        // 拿到玩家的生命上限属性实例
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return; // 理论上玩家必有该属性,这里只是防御性判断
        }

        // 只有 base 仍是原版默认 20.0 时才初始化,避免误重置已有加成的老玩家
        if (maxHealth.getBaseValue() == 20.0) {
            // 读取配置里的开局生命(默认半颗心 = 1.0)
            double newBase = Heartstealer.getConfig().initialHealth;

            // 把生命上限 base 改成配置值
            maxHealth.setBaseValue(newBase);

            // 当前血量不能超过新上限,否则会显示异常,所以取较小值钳制一下
            player.setHealth(Math.min(player.getHealth(), (float) newBase));
        }
    }

    /**
     * 击杀奖励:给击杀者累加生命上限和攻击力。
     *
     * <p>生命上限:直接把 MAX_HEALTH 的 base 加上本次奖励值(killedMaxHealth),
     * 永久保留并自动存档。</p>
     *
     * <p>攻击力:每次击杀在当前加成基础上再加 attackDamagePerKill。
     * 因为同一个 ID 只能存在一个修饰符,做法是:取出旧的 → 计算新值 → 移除旧的 → 加入新的。
     * 用 addPermanentModifier 加入,会随玩家 NBT 自动持久化。</p>
     *
     * @param player          击杀者
     * @param killedMaxHealth 本次奖励的生命值(通常是被击杀生物的最大生命)
     */
    public static void addKillReward(Player player, double killedMaxHealth) {
        // ---------- 1. 累加生命上限(并受配置 maxHealthCap 限制) ----------
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            // base 在 Mojang 映射里是 double,直接累加即可
            double newBase = maxHealth.getBaseValue() + killedMaxHealth;
            // 不能超过配置里的生命上限(默认 1024 = 原版上限;调大可突破)
            double cap = Heartstealer.getConfig().maxHealthCap;
            if (newBase > cap) {
                newBase = cap;
            }
            maxHealth.setBaseValue(newBase);
        }

        // ---------- 1.5 同时回复等量当前血量 ----------
        // 需求:击杀生物后,当前血量也要跟着加(上限加多少,当前血量就加多少),
        // 例如杀一只 3 血的鸡,生命上限 +3 的同时,当前血量也 +3。
        // 用 heal() 而不是直接 setHealth:heal() 会自动把血量钳制到不超过上限,
        // 而且走的是原版"回血"逻辑,干净安全。
        player.heal((float) killedMaxHealth);

        // ---------- 2. 累加攻击力(带固定 ID 的永久修饰符) ----------
        HeartStealerConfig config = Heartstealer.getConfig();
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return; // 理论上玩家必有攻击力属性,这里只是防御性判断
        }

        // 取出旧的加成修饰符(没有则为 null);amount() 是修饰符的数值
        AttributeModifier oldModifier = attack.getModifier(ATTACK_DAMAGE_MODIFIER_ID);
        double oldBonus = oldModifier != null ? oldModifier.amount() : 0.0;
        // 新加成 = 旧加成 + 每次击杀的增量
        double newBonus = oldBonus + config.attackDamagePerKill;

        // 同一个 ID 只能存在一个修饰符,必须先移除旧的,否则会因"重复 ID"而报错
        if (oldModifier != null) {
            attack.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
        }
        // 只有新值大于 0 才加入,避免加出一个负加成
        if (newBonus > 0) {
            attack.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    newBonus,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        // 日志:排障用 —— 能看到攻击加成、生命上限、当前血量都变了多少
        Heartstealer.LOGGER.info("[HeartStealer] 攻击力加成 -> {} (旧 {} + {}),生命上限 -> {},当前血量 -> {}",
                newBonus, oldBonus, config.attackDamagePerKill,
                maxHealth != null ? maxHealth.getBaseValue() : "?",
                player.getHealth());

        // 主动把攻击力同步给该玩家的客户端。
        // 原因:原版的属性同步机制有时不推送 AttributeModifier(只推 base 值),
        // 导致服务端加成正常、但客户端 HUD 上"攻击 +N"一直是 0。
        syncAttackToClient(player);
    }

    /**
     * 主动把玩家的"攻击力"属性同步到其客户端。
     *
     * <p>直接给玩家发一个 {@link ClientboundUpdateAttributesPacket},把攻击力属性
     * (含 base 值和所有 modifier)完整推送到客户端,客户端 HUD 就能读到最新的
     * 攻击加成,而不是一直显示 0。</p>
     *
     * @param player 击杀者(服务端玩家)
     */
    public static void syncAttackToClient(Player player) {
        // 只有服务端玩家才需要主动同步(客户端玩家没有 connection)
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        // 玩家还没连上网络时跳过(击杀时必然已连接,这行只是防御)
        if (serverPlayer.connection == null) {
            return;
        }
        AttributeInstance attack = serverPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }
        // 把"该玩家的攻击力属性"整体打包成一个网络包发给玩家客户端
        serverPlayer.connection.send(
                new ClientboundUpdateAttributesPacket(serverPlayer.getId(), java.util.List.of(attack)));
    }

    /**
     * 读取玩家当前的攻击力加成数值(供 HUD 等显示使用)。
     *
     * @return 攻击力修饰符的 amount();没有加成时返回 0
     */
    public static double getAttackBonus(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 0.0;
        }
        AttributeModifier modifier = attack.getModifier(ATTACK_DAMAGE_MODIFIER_ID);
        return modifier != null ? modifier.amount() : 0.0;
    }

    /**
     * 读取玩家当前的生命上限 base 值(供 HUD 等显示使用)。
     *
     * @return MAX_HEALTH 的 base 值;属性不存在时返回原版默认 20.0
     */
    public static double getMaxHealthBase(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return 20.0;
        }
        return maxHealth.getBaseValue();
    }
}

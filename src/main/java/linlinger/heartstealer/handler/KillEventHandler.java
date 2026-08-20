package linlinger.heartstealer.handler;

import linlinger.heartstealer.Heartstealer;
import linlinger.heartstealer.attribute.PlayerAttributeManager;
import linlinger.heartstealer.config.HeartStealerConfig;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * 击杀事件处理器 —— 订阅"生物死亡"事件,决定是否给击杀者发奖励。
 *
 * <p>职责:先判断这次死亡是不是玩家击杀的(近战 / 箭矢远程都算);
 * 是的话再判断被击杀者是否在黑名单里、以及对应类别开关(被动/敌对/Boss/PvP)
 * 是否开启;最后把奖励(生命 + 攻击力)交给 {@link PlayerAttributeManager} 发放。</p>
 *
 * <p>本类只做"判定与分发",具体属性修改在 PlayerAttributeManager 里。</p>
 */
public final class KillEventHandler {

    /** 工具类禁止实例化 */
    private KillEventHandler() {
    }

    /**
     * 注册击杀事件监听。
     * 在模组入口 {@code Heartstealer.onInitialize()} 里调用一次即可。
     */
    public static void register() {
        // AFTER_DEATH:生物(含玩家)死亡后触发,此时能拿到死亡原因和攻击者信息
        ServerLivingEntityEvents.AFTER_DEATH.register(KillEventHandler::handleDeath);
    }

    /**
     * 核心处理逻辑:一次"生物死亡"发生时的判定与奖励发放。
     *
     * @param entity       被击杀的生物(也可能是玩家,对应 PvP 场景)
     * @param damageSource 伤害来源,用于追溯是谁下的手
     */
    private static void handleDeath(LivingEntity entity, DamageSource damageSource) {
        // ---------- 1. 判定击杀者是不是玩家 ----------
        ServerPlayer killer = resolveKiller(damageSource);
        if (killer == null) {
            // 环境伤害(摔死、火烧、溺水)或生物互杀,没有玩家击杀者,不计奖励
            return;
        }

        HeartStealerConfig config = Heartstealer.getConfig();

        // ---------- 2. 黑名单检查:黑名单里的实体不计奖励 ----------
        // EntityType.getKey() 返回实体注册 ID(如 "minecraft:zombie"),toString() 转成字符串比对
        String typeId = EntityType.getKey(entity.getType()).toString();
        if (config.entityBlacklist.contains(typeId)) {
            return;
        }

        // ---------- 3. 按实体类别分支,决定奖励值并检查对应开关 ----------
        // 注意判断顺序:玩家必须先于"被动/中立"分支判断,因为玩家不是 Mob,
        // 若不先处理玩家,会被下面的 else 分支当成"被动生物"而走 includePassive 开关。
        double reward;
        if (entity instanceof Player) {
            // PvP:由 includePlayers 开关控制;奖励取对方"当前"最大生命
            if (!config.includePlayers) {
                return;
            }
            reward = entity.getMaxHealth();
        } else if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            // Boss(末影龙 / 凋灵):由 includeBoss 开关控制,奖励再乘 bossMultiplier 倍率
            if (!config.includeBoss) {
                return;
            }
            reward = entity.getMaxHealth() * config.bossMultiplier;
        } else if (entity.getType().getCategory() == MobCategory.MONSTER) {
            // 敌对生物(僵尸、骷髅、苦力怕等):由 includeHostile 开关控制。
            // 1.21.11 起原版把"生物类型"移除了,改用实体注册的"生成类别"(MobCategory):
            // MONSTER = 敌对(僵尸/骷髅/苦力怕/末影人/蜘蛛等),CREATURE = 被动动物等。
            if (!config.includeHostile) {
                return;
            }
            reward = entity.getMaxHealth();
        } else {
            // 其它:被动/中立生物(猪、牛、羊、村民等):由 includePassive 开关控制
            if (!config.includePassive) {
                return;
            }
            reward = entity.getMaxHealth();
        }

        // ---------- 4. 发放奖励:生命上限 += reward,攻击力 += attackDamagePerKill ----------
        // 日志:方便排障,能看到"哪个玩家、杀了什么、发了多少"
        Heartstealer.LOGGER.info("[HeartStealer] {} 击杀 {} ({}),奖励生命 {}",
                killer.getName().getString(), typeId, entity.getName().getString(), reward);
        PlayerAttributeManager.addKillReward(killer, reward);
    }

    /**
     * 从伤害来源中找出"玩家击杀者"。
     *
     * <p>近战 / 投掷物:damageSource.getEntity() 直接就是攻击者(玩家)。<br>
     * 远程箭矢:getEntity() 是间接攻击者,getDirectEntity() 是箭本身,
     * 此时需要通过 Projectile.getOwner() 追到射出箭的玩家。</p>
     *
     * @param damageSource 伤害来源
     * @return 玩家击杀者;如果不是玩家击杀则返回 null
     */
    private static ServerPlayer resolveKiller(DamageSource damageSource) {
        // 先看攻击者本身是不是玩家(近战、投掷物、火球等大多适用)
        Entity attacker = damageSource.getEntity();
        if (attacker instanceof ServerPlayer player) {
            return player;
        }

        // 再看直接伤害来源(如箭)的主人是不是玩家
        Entity direct = damageSource.getDirectEntity();
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }

        // 都不是玩家 → 环境伤害或生物互杀
        return null;
    }
}

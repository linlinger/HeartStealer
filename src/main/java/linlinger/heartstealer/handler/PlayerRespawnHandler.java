package linlinger.heartstealer.handler;

import linlinger.heartstealer.attribute.PlayerAttributeManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 玩家重生处理器 —— 死亡重生后把旧玩家的属性完整复制到新玩家身上(死亡无惩罚)。
 *
 * <p>原版在玩家死亡重生时会新建一个 ServerPlayer 实例,属性会被重置;
 * 通过 ServerPlayerEvents.COPY_FROM 事件(在数据覆盖前触发),把旧实例的生命上限
 * 和攻击力修饰符搬到新实例上,从而实现"死亡不掉加成"。</p>
 */
public final class PlayerRespawnHandler {

    /** 工具类禁止实例化 */
    private PlayerRespawnHandler() {
    }

    /**
     * 把 oldPlayer 的加成属性复制到 newPlayer。
     *
     * <p>生命上限:直接复制 MAX_HEALTH 的 base 值,新玩家存档会继续记住这个值。<br>
     * 攻击力:从旧实例里按固定 ID 取出修饰符,在新实例上重新 addPermanentModifier 加入,
     * 同样会被自动写入 NBT。</p>
     *
     * <p><b>血量处理(重要,防止无限死亡):</b>死亡重生时(alive=false),旧玩家
     * 的血量是 0,如果把 0 复制给新玩家,新玩家会以 0 血重生并立刻再死,形成
     * "点击重生 → 无限死亡" 的循环。所以死亡重生必须让新玩家<b>回满血</b>;
     * 只有跨维度(alive=true)时才沿用旧玩家当前血量(并钳制到新上限)。</p>
     *
     * @param oldPlayer 死亡/跨维度前的旧玩家实例
     * @param newPlayer 重生/跨维度后的新玩家实例
     * @param alive     旧玩家是否还活着(false = 死亡重生,true = 跨维度)
     */
    public static void copyPlayerData(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        // ---------- 1. 复制生命上限 base ----------
        AttributeInstance oldHealth = oldPlayer.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance newHealth = newPlayer.getAttribute(Attributes.MAX_HEALTH);
        if (oldHealth != null && newHealth != null) {
            // base 在 Mojang 映射里是 double,直接复制即可
            double newBase = oldHealth.getBaseValue();
            newHealth.setBaseValue(newBase);

            if (!alive) {
                // 死亡重生:回满血(新上限)。绝不能用死亡时的 0 血,否则无限死亡。
                newPlayer.setHealth((float) newBase);
            } else {
                // 跨维度:沿用旧玩家当前血量,但不超过新上限
                newPlayer.setHealth(Math.min(oldPlayer.getHealth(), (float) newBase));
            }
        }

        // ---------- 2. 复制攻击力修饰符 ----------
        AttributeInstance oldAttack = oldPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance newAttack = newPlayer.getAttribute(Attributes.ATTACK_DAMAGE);
        if (oldAttack != null && newAttack != null) {
            // 按固定 ID 从旧实例取出我们加的修饰符
            AttributeModifier modifier = oldAttack.getModifier(PlayerAttributeManager.ATTACK_DAMAGE_MODIFIER_ID);
            if (modifier != null) {
                // 保险起见先移除新实例上可能残留的同 ID 修饰符,再重新加入一个等值的
                newAttack.removeModifier(PlayerAttributeManager.ATTACK_DAMAGE_MODIFIER_ID);
                newAttack.addPermanentModifier(new AttributeModifier(
                        PlayerAttributeManager.ATTACK_DAMAGE_MODIFIER_ID,
                        modifier.amount(),
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // 主动把攻击力同步给新玩家的客户端,防止重生后 HUD 攻击加成显示 0。
        PlayerAttributeManager.syncAttackToClient(newPlayer);
    }
}

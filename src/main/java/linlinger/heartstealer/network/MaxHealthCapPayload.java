package linlinger.heartstealer.network;

import linlinger.heartstealer.Heartstealer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 → 客户端的自定义数据包:把"主机(服务器)的生命上限配置"同步给客户机。
 *
 * <p>为什么需要它:生命上限的钳制逻辑(AttributeCapMixin)在客户端也会跑,
 * 如果客户机用自己的本地配置(比如 1024),就会把服务端同步过来的真实血量
 * 又钳回 1024,导致联机时血条数值/渲染异常。所以主机必须把自己的
 * maxHealthCap 发给客户机,<b>以主机配置为准</b>。</p>
 *
 * @param cap 主机的 maxHealthCap 值
 */
public record MaxHealthCapPayload(double cap) implements CustomPacketPayload {

    /** 数据包类型标识(注册到网络用) */
    public static final Type<MaxHealthCapPayload> TYPE =
            new Type<>(Heartstealer.id("max_health_cap"));

    /** 序列化方式:一个 double(通过 RegistryFriendlyByteBuf 读写) */
    public static final StreamCodec<RegistryFriendlyByteBuf, MaxHealthCapPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, MaxHealthCapPayload::cap,
                    MaxHealthCapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

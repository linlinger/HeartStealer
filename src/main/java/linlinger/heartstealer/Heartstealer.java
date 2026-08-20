package linlinger.heartstealer;

import linlinger.heartstealer.attribute.PlayerAttributeManager;
import linlinger.heartstealer.config.HeartStealerConfig;
import linlinger.heartstealer.handler.KillEventHandler;
import linlinger.heartstealer.handler.PlayerRespawnHandler;
import linlinger.heartstealer.network.MaxHealthCapPayload;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Heartstealer implements ModInitializer {
	public static final String MOD_ID = "heartstealer";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/**
	 * 全局配置实例。
	 * 在 {@link #onInitialize()} 里通过 AutoConfig 从 config/heartstealer.json 读取,
	 * 之后其它类都可以用 {@link #getConfig()} 拿到同一个实例。
	 */
	private static HeartStealerConfig config;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		// 第一步:注册并读取配置。
		// 注意:AutoConfig 必须先 register 才能 getConfigHolder,否则会抛
		// "Config ... has not been registered" 异常。GsonConfigSerializer 负责
		// 把配置读写到 config/heartstealer.json(首次运行自动生成)。
		AutoConfig.register(HeartStealerConfig.class, GsonConfigSerializer::new);
		config = AutoConfig.getConfigHolder(HeartStealerConfig.class).getConfig();

		// 注册自定义网络包(服务端→客户端):用于把主机的生命上限同步给客户机。
		PayloadTypeRegistry.playS2C().register(MaxHealthCapPayload.TYPE, MaxHealthCapPayload.CODEC);

		// 第二步:玩家进入服务器时,把开局生命上限改成配置里的半颗心(initialHealth)。
		// 监听器参数类型由函数式接口自动推断(Mojang 映射下是 ServerGamePacketListenerImpl),
		// 这里直接用其公开字段 player 拿到进服的 ServerPlayer 做初始化。
		// 兼容说明:单人游戏、局域网联机、正式服务器都会触发 JOIN,
		// 所以这里的初始化对所有模式都生效。
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			PlayerAttributeManager.initPlayerHealth(handler.player);
			// 顺带把攻击力属性同步给该玩家的客户端,保证一进游戏 HUD 就显示正确加成
			// (否则要等下次击杀才会同步,进服时可能显示 0)。
			PlayerAttributeManager.syncAttackToClient(handler.player);
			// 把主机的生命上限(maxHealthCap)发给客户机,以主机配置为准,
			// 避免客户机用自己的本地配置把真实血量钳回原版 1024。
			ServerPlayNetworking.send(handler.player, new MaxHealthCapPayload(config.maxHealthCap));
		});

		// 第三步:玩家死亡重生时,如果配置开启 keepOnDeath,就把生命/攻击力完整复制到新实例。
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (config.keepOnDeath) {
				PlayerRespawnHandler.copyPlayerData(oldPlayer, newPlayer, alive);
			}
		});

		// 第四步:注册击杀事件,处理"击杀生物夺取生命与力量"。
		KillEventHandler.register();

		LOGGER.info("[HeartStealer] 已加载:开局生命 = 半颗心,击杀生物可夺取生命与力量,越战越强!");
	}

	/**
	 * 供模组其它类读取全局配置。
	 *
	 * @return 当前生效的 {@link HeartStealerConfig}
	 */
	public static HeartStealerConfig getConfig() {
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

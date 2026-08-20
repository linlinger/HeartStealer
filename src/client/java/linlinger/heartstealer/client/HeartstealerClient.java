package linlinger.heartstealer.client;

import linlinger.heartstealer.client.hud.HeartStealerHud;
import linlinger.heartstealer.config.SyncedConfig;
import linlinger.heartstealer.network.MaxHealthCapPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * 模组的客户端入口(仅客户端加载)。
 *
 * <p>在 {@code onInitializeClient} 里注册客户端专属的初始化逻辑,
 * 例如这里的 HUD 渲染回调:每一帧游戏渲染屏幕时,把
 * {@link HeartStealerHud} 的信息面板(攻击力 + 生命 + 血条指示)画上去;
 * 以及接收主机同步过来的生命上限配置。</p>
 */
public class HeartstealerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 注册 HUD 渲染回调:每帧绘制自定义信息面板。
		// 用 HUD 叠加层而不是 mixin,避免依赖未反编译的原版血条渲染方法导致崩溃。
		HudRenderCallback.EVENT.register((guiGraphics, tickCounter) ->
			HeartStealerHud.render(guiGraphics, tickCounter));

		// 接收主机同步的生命上限配置(以主机为准)。
		// 这个回调在客户端主线程执行,直接存到 SyncedConfig 即可;
		// AttributeCapMixin 会优先使用这个同步值,保证联机时血条和主机一致。
		ClientPlayNetworking.registerGlobalReceiver(MaxHealthCapPayload.TYPE,
			(payload, context) -> SyncedConfig.maxHealthCap = payload.cap());
	}
}

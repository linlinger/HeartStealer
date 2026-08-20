package linlinger.heartstealer.client.mixin;

import linlinger.heartstealer.client.hud.HeartStealerHud;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按需隐藏原版红心血条(仅本模组自定义渲染时)。
 *
 * <p>原版的红心会随生命上限增加而<b>堆叠成很多排</b>,铺满屏幕、样式也是原版的。
 * 本模组在"自定义模式"(没装 colorfulhearts 且配置开启)下把它隐藏,
 * 改由 {@code HeartStealerHud} 用自定义的红色血条 + 数字代替。</p>
 *
 * <p><b>条件化:</b>只有当 {@link HeartStealerHud#shouldUseCustomRender()} 为
 * true 时才取消渲染;如果装了彩色心心 colorfulhearts(它自己负责渲染血条,
 * 或者配置关闭了自定义渲染),就<b>不取消</b>,让原版红心照常显示 ——
 * 避免本模组和 colorfulhearts 抢着渲染同一块画面。</p>
 *
 * <p><b>安全性:</b>方法签名已用 1.21.11 官方映射核对过:
 * {@code renderHearts(GuiGraphics, Player, int, int, int, int, float, int, int, int, boolean)}。
 * 同时 injector 加了 {@code require = 0}:万一某版本方法改名/改签名,最多只是
 * "跳过不生效"(红心照常显示),绝不会导致游戏崩溃。</p>
 */
@Mixin(Gui.class)
public abstract class GuiHeartsMixin {

    /**
     * 在 renderHearts 开头判断:自定义渲染开启时才取消原版红心。
     */
    @Inject(method = "renderHearts", at = @At("HEAD"), cancellable = true, require = 0)
    private void heartstealer$hideVanillaHearts(CallbackInfo ci) {
        // 用本模组自己的渲染 → 取消原版红心;否则放行(交给 colorfulhearts / 原版)
        if (HeartStealerHud.shouldUseCustomRender()) {
            ci.cancel();
        }
    }
}

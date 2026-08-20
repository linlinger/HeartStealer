package linlinger.heartstealer.client.mixin;

import linlinger.heartstealer.client.hud.HeartStealerHud;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按需隐藏原版装甲栏(仅本模组自定义渲染时)。
 *
 * <p>原版装甲栏会画在原版红心的上方;在"自定义模式"下本模组把红心隐藏后,
 * 装甲栏的位置会错乱(位置过高、和自定义 HUD 叠在一起),所以一并取消,
 * 改由 {@code HeartStealerHud} 在原版红心正上方(血条上方 10px)自己画
 * 与原版样式一致的装甲图标。</p>
 *
 * <p><b>条件化:</b>与 {@link GuiHeartsMixin} 同理,只有本模组自定义渲染开启时
 * 才取消;装了 colorfulhearts(或配置关闭自定义渲染)时放行,让原版装甲栏
 * 照常显示 —— 这样"纯文字模式"下原版红心 + 原版装甲栏完整呈现,
 * 本模组只负责在原版装甲栏上方画两行加成文字。</p>
 *
 * <p>方法签名已确认:{@code renderArmor(GuiGraphics, Player, int, int, int, int)}
 * 是私有静态方法,注入器带 require = 0 兜底,万一版本差异只会跳过、不崩游戏。</p>
 */
@Mixin(Gui.class)
public abstract class GuiArmorMixin {

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void heartstealer$hideVanillaArmor(CallbackInfo ci) {
        // 用本模组自己的渲染 → 取消原版装甲栏;否则放行(保留原版装甲栏)
        if (HeartStealerHud.shouldUseCustomRender()) {
            ci.cancel();
        }
    }
}

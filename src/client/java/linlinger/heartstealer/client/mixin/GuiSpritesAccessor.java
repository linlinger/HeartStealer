package linlinger.heartstealer.client.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link Gui} 里的私有静态字段:装甲图标的三个贴图 ID。
 *
 * <p>原版画装甲栏用的是 {@code Gui.ARMOR_FULL_SPRITE / ARMOR_HALF_SPRITE /
 * ARMOR_EMPTY_SPRITE} 三个私有静态字段。我们用 @Accessor 把它暴露出来,
 * 这样 {@code HeartStealerHud} 自己画装甲栏时能用到一模一样的贴图,样式和原版一致。</p>
 */
@Mixin(Gui.class)
public interface GuiSpritesAccessor {

    /** 满格装甲图标(2 点护甲 = 1 个满格) */
    @Accessor("ARMOR_FULL_SPRITE")
    static Identifier getArmorFullSprite() {
        throw new AssertionError();
    }

    /** 半格装甲图标(1 点护甲) */
    @Accessor("ARMOR_HALF_SPRITE")
    static Identifier getArmorHalfSprite() {
        throw new AssertionError();
    }

    /** 空装甲图标(没有护甲时显示的空槽) */
    @Accessor("ARMOR_EMPTY_SPRITE")
    static Identifier getArmorEmptySprite() {
        throw new AssertionError();
    }
}

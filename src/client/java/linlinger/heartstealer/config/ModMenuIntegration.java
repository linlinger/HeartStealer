package linlinger.heartstealer.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;

/**
 * ModMenu 集成(可选,仅客户端)。
 *
 * <p><b>为什么放在 src/client:</b>ModMenu 的配置界面基于 Minecraft 的客户端类
 * {@code Screen},而这个项目用了 loom 的 splitEnvironmentSourceSets 把 Minecraft
 * 拆成 common(共用)和 clientOnly(纯客户端)两部分,{@code src/main} 只编译
 * common 部分,看不到 Screen,所以这个类必须放在纯客户端的 {@code src/client} 里。</p>
 *
 * <p>装了这个类之后,玩家在游戏里按 Esc → Mods → HeartStealer →
 * 点"配置"按钮,就能打开图形化配置界面,无需手改 json。</p>
 */
public class ModMenuIntegration implements ModMenuApi {

    /**
     * 返回一个"根据父界面生成配置界面"的工厂函数。
     * AutoConfig 已经帮我们做好了整个界面,这里直接取用即可。
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> AutoConfig.getConfigScreen(HeartStealerConfig.class, parent).get();
    }
}

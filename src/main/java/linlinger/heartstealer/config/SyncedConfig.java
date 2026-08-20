package linlinger.heartstealer.config;

/**
 * 存放"由服务端(主机)同步过来的配置",以主机为准。
 *
 * <p>生命上限的钳制逻辑在客户端也会执行,必须用主机同步来的上限,
 * 而不是客户机本地的配置,否则联机时客户机会把真实血量钳回本地上限
 * (例如 1024),导致血条数值和渲染异常。</p>
 *
 * <p>默认 -1 表示"还没收到同步",此时回退用本地配置。</p>
 */
public final class SyncedConfig {

    /** 服务端同步来的 maxHealthCap;-1 = 尚未同步 */
    public static volatile double maxHealthCap = -1.0;

    private SyncedConfig() {
    }
}

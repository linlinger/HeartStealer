# HeartStealer · 伤害加成模块 —— 复刻规格(SPEC)

> **用途**:本文件供开发者在其它 Mod Loader(**Fabric / Forge / NeoForge / Quilt**…)重新实现本模组的「伤害加成」功能,目标是行为完全一致。
> **版本**:v1.1 | 2026-08-13 | 参考实现:Fabric 1.21.11(见文末 §7)
> **阅读对象**:会写 Java、懂 Minecraft 伤害系统的 Mod 开发者。

---

## 1. 目标行为(做什么)

玩家击杀生物获得 **「攻击加成点数」**(每次击杀 +1,可配置,默认 1.0)。该点数要加在**玩家造成的所有武器伤害**上:

| 伤害类型 | 例子 | 是否加加成 |
|---------|------|-----------|
| 近战 | 徒手、剑、斧、横扫攻击 | ✅ 加 |
| 远程 | 弓、弩、三叉戟投掷 | ✅ 加 |
| 投掷物 | 雪球、鸡蛋、喷溅/滞留药水、末影珍珠(无伤害,无效) | ✅ 加 |
| **玩家(PvP)** | 射/砸/掷另一名玩家 | ❌ **默认不加**(配置 `pvpDamageBonus=true` 才加;近战走属性仍加,见 §4.8) |
| **TNT 爆炸(玩家点燃)** | 玩家放 TNT 点燃引爆 | ❌ **不加** |
| **重生锚爆炸** | 玩家点击重生锚引爆 | ❌ **不加** |
| **末地放床爆炸** | 玩家在末地/下界放床并尝试睡觉 | ❌ **不加** |
| 其它非玩家来源 | 苦力怕爆炸、摔落、火焰、岩浆、溺水、饥饿 | ❌ 不加(天然不计) |

**判定原则一句话**:凡是「玩家本人」或「玩家拥有的弹射物」造成的伤害都加(**目标不是玩家**时);爆炸类(TNT/床/锚)因为不是弹射物、也没有"玩家主人"记录,**天然被排除**,实现时**不需要**为它们写任何特殊判断。

---

## 2. 核心机制(怎么做)

### 2.1 注入点:伤害结算入口

在**实体受伤**的入口处拦截,在伤害值进入护甲/魔抗计算**之前**把它加上加成(这样加成和近战属性一样正常吃护甲)。

- 通用概念:每个生物受伤都会走"实体受伤(LivingEntity hurt)"逻辑,伤害值作为参数传入。
- 现代版本(1.21.2+)服务端入口:**`hurtServer(ServerLevel, DamageSource, float)`**,`float` 就是原始伤害值。
- 旧版本/部分 loader:`hurt(DamageSource, float)` 或事件 `LivingHurtEvent`(Forge)。

### 2.2 判定逻辑(与 loader 无关,直接照抄思路)

```
对每次受伤:
  1. direct = source.getDirectEntity()   // "直接造成伤害的实体"
  2. 若 direct 是「弹射物」(Projectile / AbstractArrow / ThrownPotion / Snowball / ThrownTrident…):
        owner = direct.getOwner()          // 弹射物的"主人"
        若 owner 是玩家:
            若 target(受伤者)是玩家 且 配置 pvpDamageBonus = false:
                不加(PvP 默认原版伤害)          // ❌
            否则:
                伤害值 += 该玩家的攻击加成点数   // ✅ 命中
      否则:
            不加(骷髅射的箭、自然爆炸等)     // ❌
  3. 若 direct 不是弹射物(玩家本人 / TNT 实体 / 无):
        不加(近战走属性加成;TNT/床/锚爆炸天然不计)  // ❌
```

> 为什么近战不在事件里加?近战已经通过 `ATTACK_DAMAGE` 属性加成过一次(见 §3)。如果事件里对"所有玩家伤害"都加,近战会**双倍**。所以事件逻辑**只处理"弹射物"分支**。

### 2.3 三种爆炸为什么天然排除(不需要特殊代码)

| 爆炸 | DamageSource 的直接实体 | 判定结果 |
|------|------------------------|---------|
| TNT(玩家点燃) | 点燃的 TNT 实体(不是弹射物) | 不命中 ❌ |
| 重生锚 | 无(空) | 不命中 ❌ |
| 末地床 | 无(空) | 不命中 ❌ |
| 苦力怕 | 苦力怕本体(不是玩家弹射物) | 不命中 ❌ |

---

## 3. 数据模型(攻击加成点数怎么存)

- **概念**:「攻击加成点数」是一个数字(默认从 0 开始,每次击杀 +`attackDamagePerKill`,默认 1.0)。
- **要求**:必须随玩家存档持久化(死亡 / 重登 / 重进都不丢),并且**近战、远程、HUD 显示读到的是同一个数**。
- **两种推荐实现**:
  - **方案 A(推荐,通用性强)**:注册一个**自定义属性(Attribute)** 存储点数。原版属性天然支持存档、跨维度、复制。伤害事件里读 `玩家.getAttributeValue(自定义属性)`。
  - **方案 B(Fabric 本模组现状)**:把点数作为 `ATTACK_DAMAGE` 属性上的一个**固定 ID 永久修饰符**(AttributeModifier)。近战直接由原版吃这个加成;远程/投掷物在伤害事件里读同一个修饰符的 amount。优点:不用注册新属性、不用改存档;缺点:近战加成会显示在物品/属性面板上。
- 无论哪种,务必保证**近战与远程共用同一个点数**,否则数值会不一致。
- **⚠ 攻击力有原版上限(重要)**:`ATTACK_DAMAGE` 属性注册为 `RangedAttribute("attribute.name.attack_damage", 1.0, 0.0, 2048.0)`(**上限 2048**,字节码确认)。若不加处理,近战加成累计超过 2048 会被原版钳死不再增长,而远程/投掷物(事件里直接加伤害值)不受钳制 → **后期近战和远程数值不一致**。复刻时必须像"生命上限突破 1024"一样,给 `ATTACK_DAMAGE` 也放开上限(拦截属性钳制方法,对攻击力只保下限不设上限)。

---

## 4. 关键陷阱(复刻时最容易翻车的地方)

1. **近战双倍** ❗最致命:不要在伤害事件里对"所有玩家伤害"无条件加,否则近战 = 属性加成 + 事件加成 = 双倍。**事件里只认"弹射物 + 主人是玩家"**。
2. **无敌帧分支(现代版本)**:`hurtServer` 内部 `actuallyHurt` 可能被调用**两次**(if/else 互斥:无敌帧内只结算超出部分 / 正常结算)。两种实现都可行,二选一:**(a) 改方法入口的伤害值参数**(改一次,两个分支都用新值);**(b) 用 `@Redirect` 同时接管两个 `actuallyHurt` 调用点** —— 因为两个分支互斥,运行时只执行一个,所以加成只加一次。本模组用 (b) 实测成功。
3. **护甲顺序**:加成要在**护甲/魔抗减免之前**加入,这样加成吃护甲,和近战一致。用 (a) 改入口参数天然满足;用 (b) 时,护甲/魔抗减免发生在 `actuallyHurt` **内部**,把加成加到传给它的参数上同样是在减免前生效。
4. **药水**:喷溅/滞留伤害药水也是弹射物(owner=玩家),会命中 → 符合需求(投掷物加伤害)。
5. **横扫攻击**:横扫的伤害来源实体是玩家本人(不是弹射物),走近战属性加成,事件不会重复加 → 不会双倍。
6. **只认玩家的弹射物**:骷髅射的箭、掠夺者弩箭等 owner 不是玩家,不能加。
7. **判定"直接实体"而非"来源实体"**:`source.getDirectEntity()` 是弹射物本体;`source.getEntity()`/`getCausingEntity()` 可能已是玩家。用 `getDirectEntity()` 才能正确识别弹射物、并借此排除 TNT/床/锚。
8. **PvP 开关(默认关闭)**:被打目标(target)是玩家时,默认**不加**加成(原版伤害),由配置 `pvpDamageBonus`(默认 false)控制。注意该开关只影响"弹射物伤害加成";近战因为走原版 `ATTACK_DAMAGE` 属性,打玩家仍带加成(属性无法按目标区分,除非把近战也改成事件计算)。
9. **攻击力 2048 上限**:见 §3 末尾 —— 记得放开 `ATTACK_DAMAGE` 属性的钳制上限,否则近战后期不增长、与远程不一致。

---

## 5. 各 Mod Loader 实现建议

### 5.1 Fabric(本模组现状)
- **Mixin(推荐,已实测)**:`@Redirect` 重定向 `hurtServer` 内部对 `actuallyHurt` 的调用,在调用前给伤害值加加成:
  ```java
  @Mixin(LivingEntity.class)
  public abstract class LivingEntityHurtMixin {
      @Shadow protected abstract void actuallyHurt(ServerLevel level, DamageSource source, float amount);

      @Redirect(method = "hurtServer",
              at = @At(value = "INVOKE",
                      target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt("
                              + "Lnet/minecraft/server/level/ServerLevel;"
                              + "Lnet/minecraft/world/damagesource/DamageSource;F)V"))
      private void heartstealer$addProjectileDamage(
              LivingEntity target, ServerLevel level, DamageSource source, float amount) {
          float finalAmount = amount;
          if (source.getDirectEntity() instanceof Projectile p
                  && p.getOwner() instanceof Player attacker) {
              double bonus = 读玩家的攻击加成点数(attacker);
              if (bonus > 0) finalAmount = amount + (float) bonus;
          }
          ((LivingEntityHurtMixin) (Object) target).actuallyHurt(level, source, finalAmount);
      }
  }
  ```
- **⚠ 踩坑记录(Fabric/mixin 0.8.x)**:不要用 `@ModifyVariable(argsOnly=true, ordinal=…)` 直接改 `hurtServer` 的入口参数 —— 其 handler 签名在不同 mixin 版本下对"是否包含被修改参数"的解析不一致,很容易注入失败;且若写 `require=0` 会**静默失败**(游戏不崩、加成不生效,极难排查)。`@Redirect` 的 handler 签名是标准的 `(目标对象, 目标方法参数…)`,无歧义;require 用默认值,注入失败会明确报错。
- **或 Fabric API 事件**:若所用版本提供了"可改伤害值"的事件回调(如某些 Fabric API 版本),用事件替代 Mixin,判定逻辑不变。

### 5.2 Forge
- **`LivingHurtEvent`**(`net.minecraftforge.event.entity.living.LivingHurtEvent`):
  ```java
  @SubscribeEvent
  public void onLivingHurt(LivingHurtEvent event) {
      Entity direct = event.getSource().getDirectEntity();
      if (!(direct instanceof Projectile p)) return;
      if (!(p.getOwner() instanceof Player attacker)) return;
      double bonus = 读玩家的攻击加成点数(attacker);
      event.setAmount(event.getAmount() + (float) bonus);
  }
  ```
- Forge 的 `LivingHurtEvent` 在**护甲减免前**触发,`setAmount` 天然满足"加成吃护甲"。

### 5.3 NeoForge
- **`LivingIncomingDamageEvent`**(或版本对应的 `LivingHurtEvent`):
  ```java
  event.setAmount(event.getAmount() + (float) bonus);  // 判定同上
  ```

### 5.4 通用检查清单(写完后对照)
- [ ] 近战加成来源 = 属性,事件里不加近战
- [ ] 事件里只处理 `getDirectEntity() instanceof Projectile && getOwner() instanceof Player`
- [ ] 被打目标是玩家且 `pvpDamageBonus=false` → 不加(原版伤害);打怪始终加
- [ ] `ATTACK_DAMAGE` 属性上限 2048 已放开(近战与远程长期一致)
- [ ] 加成数值与 HUD「攻击 +N」一致
- [ ] TNT / 床 / 锚爆炸:即使玩家"导致"了爆炸,也不加成(天然满足,不要手动加排除)

---

## 6. 验收测试清单(给测试者/复刻者)

| # | 场景 | 期望 |
|---|------|------|
| 1 | 徒手打鸡(加成 +N) | 伤害 = 1 + N |
| 2 | 铁剑打僵尸 | 伤害 = 剑基础伤害 + N |
| 3 | 横扫攻击打旁边怪 | 不双倍(近战只加一次) |
| 4 | 满力弓射僵尸 | 伤害 = 箭伤害 + N |
| 5 | 三叉戟投掷 | 伤害 = 三叉戟投掷伤害 + N |
| 6 | 雪球/鸡蛋打怪 | 有加成后能打出 N 伤害 |
| 7 | 喷溅伤害药水 | 伤害 = 药水伤害 + N |
| 8 | 点燃 TNT 炸僵尸 | 伤害 = 原爆炸伤害,**不加** N |
| 9 | 末地放床爆炸 | **不加** N |
| 10 | 重生锚爆炸 | **不加** N |
| 11 | 苦力怕爆炸 | **不加** N |
| 12 | 骷髅射箭打怪(旁观) | **不加** N |
| 13 | 击杀几次后看 HUD | 「攻击 +N」与实测伤害增量一致 |
| 14 | 死亡重登 | 加成保留,数值不变 |
| 15 | 弓箭打另一玩家(默认) | 原版伤害,**不加** N |
| 16 | 弓箭打另一玩家(开 `pvpDamageBonus`) | 加 N |
| 17 | 攻击加成累计超过 2048 后打怪 | 近战和远程都继续增长(不钳死) |

---

## 7. 参考实现(Fabric 1.21.11,本模组)

- **文件**:`src/main/java/linlinger/heartstealer/mixin/LivingEntityHurtMixin.java`(核心)
- **加成读取**:`src/main/java/linlinger/heartstealer/attribute/PlayerAttributeManager.java` → `getAttackBonus(Player)`
- **1.21.11 映射坑(别的 loader 注意对应版本)**:
  - `hurtServer` 签名 = `(ServerLevel, DamageSource, float)` —— 比 1.21.1 多一个 `ServerLevel` 参数;
  - `Projectile` 类在 **`net.minecraft.world.entity.projectile.Projectile`**(不再是 `world.entity.Projectile`);
  - 弹射物继承链:`AbstractArrow`、`ThrownPotion`、`Snowball`、`ThrownTrident` 等都继承 `Projectile`。

# Narcissus

低占用，轻量化的Minecraft Limbo服务器。

## 无登录模式

本项目已移除内置账号体系：`/register`、`/login`、`/2fa`、`accounts.yml`、`staffAccount.yml` 与 TOTP 相关代码均已删除。玩家进入代理时不再被强制跳转到 Limbo 登录，而是遵循 BungeeCord 自身的默认服务器路由。Limbo 仅在以下情况使用：

* 玩家主动执行 `/limbo`；
* 玩家所在后端服务器关闭或踢出时自动兜底（`ServerKickEvent`）；
* 玩家在 Limbo 中执行 `/lobby`（或 `/hub`）返回 `settings.lobby_server` 指定的大厅。

`settings.login_target_server` 仍会被兼容读取（作为 `lobby_server` 的回退值），但新配置请使用 `settings.lobby_server`。原先仅限 Staff 的 `/time` 现在对所有玩家开放，且只影响该玩家自己客户端显示的时间。

## 当前注册的原生协议

* 1.8.X (47)
* 1.21.11 (774)
* 26.1 / 26.1.1 / 26.1.2 (775)
* 26.2 (776)
* 26.3 (777)

774 对应 **1.21.11**，不是 1.21.4（769）。以上是此插件适配的版本目标，不包含快照或预发布协议。旧版 1.9–1.12 的协议类虽仍保留在源码中，但当前网络管理器未注册它们。

出站编码器、默认地图、测试夹具和 Gradle Wrapper 已补齐。正式 Gradle 构建通过：核心 213 项、插件 8 项测试全部通过；实际插件 JAR 的 26.1（37 个）、26.2（36 个）、26.3（36 个）出站包体通过 Mojang 官方编解码器校验，标签包使用真实资源而非空占位数据。26.1 使用独立的登录/加入世界/团队格式，26.3 使用 VarInt 游戏模式和字节数组光照掩码。真实客户端和 BungeeCord 的端到端连接仍待实测。

775/776/777 的 `update_tags.bin` 使用对应官方服务端的原版数据包、TagLoader 和网络编码器生成；动态标签 ID 与实际发送的注册表条目顺序一致。26.2 注册表已更新为 29 个，包含 `sulfur_cube_hot` 损伤类型和新增的生物群系。配置阶段回归覆盖四个现代协议的接受/拒绝已知资源包分支，以及 `registry_data → update_tags → finish_configuration` 的真实编码队列；缺少必需资源会在协议初始化时报出具体路径，不再延迟到客户端连接时发生空指针。

旧地图中的床方块实体按目标协议处理：774/775 保留并发送床 NBT；776/777 对已移除的 `minecraft:bed` 实体正常省略，不发“未知类型”警告，也不会误用现在对应导管的类型 ID 25。床的颜色、方向和床头/床尾仍由普通方块状态发送；源地图 NBT 保持不变，真正未知的实体类型仍会告警。

## 构建及产物

使用 JDK 17 或更高版本运行 `./gradlew test :narcissus-bootstrap:jar`；若用 JDK 21 编译，可加 `-PjavaVersion=21`，产物仍为 Java 17 字节码。插件 JAR 为 [narcissus-bootstrap/build/libs/narcissus-bootstrap-1.0.0-SNAPSHOT.jar](narcissus-bootstrap/build/libs/narcissus-bootstrap-1.0.0-SNAPSHOT.jar)，包含核心、默认配置/地图和各版本注册表，不包含测试桩或代理提供的 BungeeCord/SnakeYAML 库。

默认 `settings.allow-version` 为 `[47, 774, 775, 776, 777]`。已有配置中的显式允许列表不会被自动覆盖；升级时请按需加入新协议号。BungeeCord 本身也必须能接受对应客户端版本，本项目不进行 ViaVersion 协议转换。


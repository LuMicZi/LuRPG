# LuRPG

一款基于 Paper 26.2 的 Minecraft RPG 插件，包含职业、技能、属性、物品、套装、药水、经验、GUI 等完整 RPG 系统。

## 功能特性

### 职业系统
- **战士** - 高血量高防御，怒气资源系统
- **法师** - 高魔法输出，法力资源系统
- **刺客** - 高暴击高机动，能量资源系统

### 属性系统
- 物理攻击 / 魔法攻击 / 真实伤害
- 物理防御 / 魔法防御
- 暴击率 / 暴击伤害
- 吸血 / 最大生命
- 移动速度
- 元素伤害：火 / 冰 / 雷 / 暗 / 光

### 技能系统
- 主动技能（右键施放）
- 被动技能（攻击/受击/击杀触发）
- 技能学习与装备
- 冷却与资源消耗管理
- 可扩展的 Skill API

### 物品系统
- RPG 物品 NBT 数据存储
- 动态 Lore 生成（4 种稀有度模板）
- 等级与职业要求
- CraftEngine 深度集成

### 战斗系统
- 自定义伤害计算
- 暴击判定
- 吸血回复
- 元素效果（燃烧/减速/眩晕/凋零/治疗）

### 套装系统
- 2件套 / 4件套增益
- 特殊效果（伤害反射/法力回复等）

### 药水系统
- 治疗 / 恢复 / 增益 / 解毒 / 资源恢复
- 冷却与叠加上限

### 经验系统
- 击杀怪物 / MythicMobs 经验
- PvP 经验
- 采集经验（挖矿/伐木/种植）

### GUI 界面
- 主菜单
- 武器列表
- 套装列表
- 药水列表
- 技能列表
- 技能学习
- 角色面板
- 职业选择

## 依赖要求

| 依赖 | 类型 | 说明 |
|------|------|------|
| Paper 26.2 | 必需 | 服务端核心 |
| Java 25+ | 必需 | 运行环境 |
| CraftEngine | 可选 | 自定义物品集成 |
| Vault | 可选 | 经济系统 |
| PlaceholderAPI | 可选 | 变量扩展 |
| MythicMobs | 可选 | 自定义怪物经验 |

## 命令

### 玩家命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/rpg` | 打开主菜单 | `lurpg.player` |
| `/rpg panel` | 角色面板 | `lurpg.player` |
| `/rpg info` | 角色信息 | `lurpg.player` |
| `/class select` | 选择职业 | `lurpg.player` |
| `/class info` | 职业信息 | `lurpg.player` |
| `/skill list` | 技能列表 | `lurpg.player` |
| `/skill cast <技能>` | 施放技能 | `lurpg.player` |
| `/skill equip <技能>` | 装备技能 | `lurpg.player` |
| `/skill unequip <槽位>` | 卸下技能 | `lurpg.player` |

### 管理员命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/rpgadmin reload` | 重载配置 | `lurpg.admin` |
| `/rpgadmin give <玩家> <物品> [数量]` | 给予物品 | `lurpg.admin` |
| `/rpgadmin setlevel <玩家> <等级>` | 设置等级 | `lurpg.admin` |
| `/rpgadmin setclass <玩家> <职业>` | 设置职业 | `lurpg.admin` |
| `/rpgadmin givexp <玩家> <数量>` | 给予经验 | `lurpg.admin` |
| `/rpgadmin giveskillpoint <玩家> <数量>` | 给予技能点 | `lurpg.admin` |
| `/rpgadmin item setstat <属性> <值>` | 设置手持物品属性 | `lurpg.admin` |
| `/rpgadmin item addstat <属性> <值>` | 增加手持物品属性 | `lurpg.admin` |
| `/rpgadmin item setid <物品ID>` | 设置物品 RPG ID | `lurpg.admin` |
| `/rpgadmin item setlevel <等级>` | 设置物品等级要求 | `lurpg.admin` |
| `/rpgadmin item addskill <技能ID>` | 添加绑定技能 | `lurpg.admin` |
| `/rpgadmin item removeskill <技能ID>` | 移除绑定技能 | `lurpg.admin` |
| `/rpgadmin item relore` | 重新生成物品 Lore | `lurpg.admin` |
| `/rpgadmin item info` | 查看物品 RPG 数据 | `lurpg.admin` |
| `/rpgadmin create weapon <ID> <名称>` | 创建自定义武器 | `lurpg.admin` |
| `/rpgadmin create armor <ID> <名称>` | 创建自定义装备 | `lurpg.admin` |
| `/rpgadmin create consumable <ID> <名称>` | 创建自定义消耗品 | `lurpg.admin` |
| `/rpgadmin create save` | 保存手持物品到配置 | `lurpg.admin` |

## 配置文件

```
plugins/LuRPG/
├── config.yml          # 主配置
├── messages.yml        # 消息配置
├── classes.yml         # 职业配置
├── skills.yml          # 技能配置
├── items.yml           # 物品配置
├── armor_sets.yml      # 套装配置
├── potions.yml         # 药水配置
├── data.db             # SQLite 数据库（自动生成）
└── lore_templates/     # Lore 模板
    ├── legendary.yml
    ├── epic.yml
    ├── rare.yml
    └── common.yml
```

## 数据库

支持两种数据库模式，在 `config.yml` 中配置：

### SQLite（默认）
```yaml
database:
  type: sqlite
  sqlite:
    file: data.db
```

### MySQL
```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: lurpg
    username: root
    password: password
```

## 构建

```bash
mvn clean package
```

构建产物位于 `target/LuRPG-<version>.jar`。

## 版本历史

### v1.4.0 (2026-09-16)
- 新增强化系统（最高 +15，每级 +5% 全属性）
- 强化成功率随等级递减（100% → 2%）
- 强化失败掉级，高级有概率销毁（可配置）
- 强化消耗金币（Vault 支持）
- 强化 GUI 界面（/rpg enhance）
- 新增宝石镶嵌系统（8 个镶嵌槽位）
- 11 种预设宝石（攻击/防御/生命/暴击/吸血等）
- 镶嵌 GUI 界面（/rpg socket）
- 宝石拆除功能，有概率保留宝石
- Lore 显示强化等级前缀、镶嵌宝石列表
- 属性计算：(基础 × 品级 × 强化) + 宝石
- 新增管理指令：
  - `/rpgadmin item setenhance <等级>` - 设置强化等级
  - `/rpgadmin item givegem <宝石ID> [数量]` - 给予宝石
  - `/rpgadmin item socket <槽位> <宝石ID>` - 镶嵌宝石
  - `/rpgadmin item unsocket <槽位>` - 拆除宝石
- 配置文件：`item_enhance.yml`、`gems.yml`

### v1.3.0 (2026-09-16)
- 新增品级系统（7个品级：粗糙/普通/精良/优秀/史诗/传说/神话）
- 品级影响物品所有属性倍率（70% ~ 175%）
- 物品获得时可随机生成品级（按权重）
- Lore 显示品级名称和属性增益百分比
- 新增品级修改符消耗品，右键使用升级装备品级
- 不同品级升级成功率不同（越高越难升）
- 新增管理指令：
  - `/rpgadmin item settier <品级ID>` - 设置物品品级
  - `/rpgadmin item randomtier` - 随机物品品级
  - `/rpgadmin item givetierupgrade [数量]` - 给予品级修改符
- 配置文件：`item_tiers.yml`

### v1.2.0 (2026-09-16)
- 新增物品"特殊效果"Lore 展示（MMOItems 风格）
- 史诗/传说品质默认显示特殊效果
- 每条效果支持"名称|描述"格式，自动分两行显示
- 新增 3 个管理指令：
  - `/rpgadmin item addspecial <名称|描述>` - 添加特殊效果
  - `/rpgadmin item removespecial <序号>` - 移除特殊效果
  - `/rpgadmin item clearspecial` - 清空特殊效果
- 特殊效果保存在物品 NBT 中，支持保存到 items.yml
- Lore 模板可配置特殊效果标题和格式

### v1.1.0 (2026-09-16)
- 装备被动技能穿戴即生效（头盔/胸甲/护腿/靴子/主手/副手）
- 攻击、受击、击杀时自动触发装备上的被动技能
- 切换武器时有被动技能激活提示
- 新增 `/rpgadmin create` 自定义物品创建指令组
  - `create weapon <ID> <名称>` - 创建自定义武器
  - `create armor <ID> <名称>` - 创建自定义装备
  - `create consumable <ID> <名称>` - 创建自定义消耗品
  - `create save` - 保存手持物品到配置文件
- 以手持物品为模板，保存到 items.yml 并自动重载

### v1.0.7 (2026-09-16)
- 新增武器绑定技能右键施放功能
- 手持绑定了技能的武器，右键即可施放该技能
- 物品技能优先于装备技能施放
- 如果武器没有绑定技能，则回退到玩家装备的技能

### v1.0.6 (2026-09-16)
- 修复物品属性战斗不生效的问题
- 现在 PDC 存储的属性（通过 /rpgadmin item setstat 添加的）会正确参与伤害计算
- 配置属性 + PDC 属性叠加生效
- 即使物品没有配置文件定义，只要 PDC 中有属性就会生效

### v1.0.5 (2026-09-16)
- 全面美化物品 Lore 显示
- 属性前添加图标符号（⚔物理攻击/⚡暴击率/🔥火焰伤害等）
- 稀有度标签美化（◇普通/◆稀有/★史诗/✦传说）
- 添加物品描述/背景故事区域
- 技能区显示技能名+简短描述
- 等级/职业要求分行显示
- 4 种稀有度各有独立配色风格
- 物品类型标签（武器/护甲/药水/饰品）

### v1.0.4 (2026-09-16)
- 新增 `/skill unlearn <技能ID>` 卸载技能命令
- 遗忘技能后返还 1 点技能点
- 如果技能已装备，会自动卸下再遗忘
- 帮助菜单和 Tab 补全已更新

### v1.0.3 (2026-09-16)
- 帮助信息支持鼠标点击执行命令（/rpg help 中的命令可直接点击）
- 悬停显示提示文字，点击直接运行或填充命令
- `/rpg` `/class` `/skill` 三个命令的帮助均已优化

### v1.0.2 (2026-09-16)
- `/rpg` 无参数时直接打开主菜单 GUI，`/rpg help` 查看帮助
- 新增物品 RPG 化指令组 `/rpgadmin item`
  - `setstat <属性> <值>` - 设置物品属性
  - `addstat <属性> <值>` - 增加物品属性
  - `setid <物品ID>` - 设置 RPG 物品 ID
  - `setlevel <等级>` - 设置等级要求
  - `addskill <技能ID>` - 添加绑定技能
  - `removeskill <技能ID>` - 移除绑定技能
  - `relore` - 重新生成物品 Lore
  - `info` - 查看物品 RPG 数据
- 支持 CE 武器/时装通过指令添加 RPG 属性和 Lore
- 属性数据存储在物品 PersistentDataContainer 中

### v1.0.1 (2026-09-15)
- 修复 SQLite 原生库加载失败问题（移除 SQLite 包重定位）
- 添加启动步骤日志，方便排查问题
- 为所有命令添加异常捕获和错误日志
- CraftEngine 改为软依赖

### v1.0.0 (2026-08-12)
- 初始版本发布
- 完整的职业/属性/技能/物品/战斗系统
- CraftEngine / Vault / PlaceholderAPI / MythicMobs 集成
- 全套 GUI 界面
- SQLite / MySQL 双模式数据库支持

## 作者

LuMicZi

## 开源协议

本项目仅供学习交流使用。

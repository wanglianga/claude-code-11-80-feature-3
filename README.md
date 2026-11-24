# 防汛平台状态迁移与规则升级服务

某市防汛平台在汛期中途把固定阈值风险规则升级为多因子规则（同时考虑**上游水位趋势、泵站启停滞后、传感器可信度**）。
旧作业保存的是**网格窗口状态**，新规则需要**泵站—河段—道路三层中间状态**。本服务提供状态迁移与规则升级的全流程治理能力：

- **流定义登记表**：输入主题、事件时间字段、水位基准、状态模式、规则包版本、分区→道路/泵站/河段映射；
- **检查点清单**：每个分区的消费位点、窗口水印、状态快照、输出批次，并提供检查点比对（位点/水印滞后与就绪度）；
- **状态语义证明器**：比较旧新字段、聚合粒度与依赖关系，给出旧状态确实可转换的守恒条件；
- **迁移计划器**：按分区选择 `ADAPT`（经证明的状态适配）/ `REPLAY`（限定时间窗回放）/ `MANUAL`（人工裁定），支持混合路线；
- **双轨校验器**：旧新规则在同一输入截点并行计算并量化风险差异；
- **切换闸门**：仅当关键分区满足证据要求且覆盖率达标时发布新输出，否则维持旧输出并标记待验证；
- **回退控制器**：保留旧规则只读快照，撤回指定范围内的新输出；
- **事件证据账**：每次风险结论记录输入、规则与状态版本，区分 `OLD_OUTPUT` / `NEW_CANDIDATE` / `NEW_OUTPUT` / `RETRACTED`；
- **路网可达性影响（新增）**：按水深时序把路段封闭阈值与状态有效期相交成**带缺测边界的时变通行图**，
  逐时精确最短路计算消防站/医院/避难点的**可达街区、绕行距离变化与被孤立人口下界**，
  并以**版本账**管理水深补测/封路更正产生的影响快照。该影响图独立存储，**不回写作业迁移状态**。

## 技术栈

Java 25 · Spring Boot 4.0 · Spring Data JPA · PostgreSQL 18 · springdoc-openapi（Swagger UI）· Maven

## 快速开始

### 方式一：Docker Compose 一键启动（应用 + PostgreSQL）

```bash
docker compose up --build
```

启动后访问 http://localhost:8080/swagger-ui.html 。

### 方式二：本地开发

前置：JDK 25、Maven 3.9+、PostgreSQL（库/用户/密码均为 `ccdev`，见 `src/main/resources/application.yml`，可用 `DB_URL/DB_USER/DB_PASSWORD` 覆盖）。

```bash
# 运行测试（使用 ccdev_test 库，首次需创建：CREATE DATABASE ccdev_test OWNER ccdev;）
mvn test

# 打包并启动（首次启动自动建表并写入演示数据）
mvn package -DskipTests
java -jar target/state-migration-service-1.0.0.jar
```

### 测试账号（HTTP Basic）

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `operator` | `operator123` | OPERATOR |
| `admin` | `admin123` | ADMIN, OPERATOR |

Swagger UI（`/swagger-ui.html`）与 OpenAPI 文档（`/v3/api-docs`）无需登录；`/api/**` 需上述账号。

## 接口一览

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 流定义 | `POST /api/streams`、`GET /api/streams[/{id}]` | 登记/查询流定义 |
| 规则包 | `POST /api/streams/{id}/rule-packages`、`GET .../rule-packages` | 登记/二次修订；多因子修订使相关比较与证明过期并重新绑定目标版本 |
| 流事件 | `POST /api/streams/{id}/events`、`GET .../events` | 事件写入（位点按分区自增）与查询 |
| 检查点 | `POST /api/streams/{id}/checkpoints`、`GET .../checkpoints` | 登记/查询检查点清单 |
| 检查点比对 | `GET /api/streams/{id}/checkpoint-compare` | 位点/水印滞后、快照与迁移就绪度比对 |
| 证明器 | `POST /api/streams/{id}/proofs`、`GET /api/proofs/{id}` | 生成状态语义证明（守恒条件） |
| 迁移编排 | `POST /api/streams/{id}/migration-plans`、`POST .../execute-all`、`POST /api/migration-plans/{id}/execute`、`POST /api/migration-plans/{id}/adjudicate` | 计划生成/执行/人工裁定 |
| 双轨校验 | `POST /api/streams/{id}/dual-track-runs`、`GET /api/dual-track-runs/{id}/diffs` | 同截点并行计算与差异量化 |
| 切换闸门 | `POST /api/streams/{id}/gate/evaluate`、`POST .../gate/release`、`GET .../gate` | 评估/发布/状态 |
| 回退控制 | `POST /api/streams/{id}/rollbacks`、`GET .../rollbacks` | 撤回输出范围、旧快照只读 |
| 证据账 | `GET /api/streams/{id}/evidence?partitionNo=&kind=` | 按分区/类别查询证据 |
| 路网可达性-路网 | `POST/GET /api/access/networks`、`GET .../{id}` | 登记路网（时间轴/时间片/水深有效期） |
| 路网可达性-路段 | `POST/GET /api/access/networks/{id}/links` | 路段：方向、限高/限载、桥涵、替代通道、封闭阈值、有效期 |
| 路网可达性-街区/服务区 | `POST .../{id}/blocks`、`POST/GET .../{id}/facilities` | 街区人口下界；消防站/医院/避难点（含入口独立阈值） |
| 路网可达性-水深 | `POST/GET .../{id}/depths`、`POST .../depths/backfill[-batch]` | 观测写入；补测（单条/批次）即出新版本快照 |
| 路网可达性-封路更正 | `POST/GET .../{id}/closures` | 区间强制封闭/放行，即出新版本快照（缺测不被放行掩盖） |
| 路网可达性-快照 | `POST .../{id}/snapshots/recompute`、`GET .../{id}/snapshots[/current]` | 重算出快照、版本账 |
| 路网可达性-影响读取 | `GET /api/access/snapshots/{id}[/affected|/facility-impact]` | 快照详情、受影响服务点、分服务区影响摘要 |

## 迁移路线选择语义

- **状态适配（ADAPT）**：旧聚合拆解/提升为三层状态，延迟小。仅当守恒条件满足时选用：
  - `PUMP_ON_OFF_CONSERVATION`：由启停事件推导的窗口内泵运行时长须与快照累计量一致（容差 1 分钟）；
  - `WINDOW_GRANULARITY_MATCH`：旧窗口须为约两小时，与新规则累计粒度一致。
  快照缺失启停事件时**适配器必须拒绝该快照，禁止以零值填充缺失启停区间**；执行时若发现累计量不守恒，计划器自动**转为限定时间窗回放**。
- **限定窗口回放（REPLAY）**：从冻结位点对受影响分区回放原始事件重算三层状态，语义最直接；回放窗口受事件保留范围约束，保留不足时降级为**人工裁定（MANUAL）**。
- 计划器依据状态可逆性（证明结论）、事件保留范围与汛情时效（关键分区优先适配）为各分区选择或混合两条路线。

## 关键业务规则

1. 新规则把某道路由低风险改为高风险、而旧作业尚未完成该分区检查点时，闸门**维持旧输出**并把该分区标记为**待验证**（`pendingVerification`），补齐检查点后方可放行。
2. 双轨期间规则二次修订（如可信度规则 v2.0.0→v2.1.0）：相关双轨比较与适配证明**过期**，引用旧证明的计划置为 `STALE`，目标版本**重新绑定**到新规则包，须重新证明与双轨后才能发布。
3. 回放分区越过切换水印时，证据账区分：已发布新输出（越水印部分**撤回**为 `RETRACTED`）、本次回放结果仅作**新候选**（`NEW_CANDIDATE`）、旧输出（`OLD_OUTPUT`）保持不变。
4. 回退保留旧规则**只读快照**，撤回 `[fromOffset, toOffset]` 范围内的新输出，闸门回到 `HOLD`，流恢复旧规则服务。

## 路网可达性影响（新增模块）

值守人员需要知道一次积水会**切断哪些应急服务区**，而不只是看到网格风险颜色。本模块在作业迁移链路之外独立建模，
**不回写**流定义/检查点/闸门等迁移状态。

### 建模与计算口径

- **路段资料**：方向（双向/单行）、限高/限载、桥涵类型（ROAD/BRIDGE/CULVERT/TUNNEL）、替代通道分组、
  积水封闭阈值 `depthCloseThresholdM`、路段自身状态有效期 `validFrom/validTo`。
- **时变通行图**：把积水窗口（逐时间片水深）与各路段**封闭阈值**、**状态有效期**、封路更正**相交**，
  得到逐时间片的 `OPEN/CLOSED/UNKNOWN` 状态。
- **缺测边界（强约束）**：时间片上无水深观测、或最近观测超出 `depthValidityMinutes` 有效期，即判 `UNKNOWN`。
  最短路把 `UNKNOWN` 与 `CLOSED` 一样**阻断**——**缺测支路绝不能当成畅通证据，也不能成为绕行路径**；
  人工“放行”同样不能掩盖缺测。
- **逐时精确最短路**：每个时间片用当时通行状态独立重算 Dijkstra（权重为路段长度米），
  可如实表达短时开闭与绕行；再用全畅通基线计算正常最短路，二者之差即**绕行距离变化**。
- **服务区影响**：对消防站/医院/避难点分别给出逐时间片**可达街区**、各街区**绕行距离增量与路径**、
  **被孤立人口下界**（仅累计登记常住人口）。服务区入口可挂独立阈值（如医院急诊 0.4m），入口封闭时该服务区对外不可用。
- **版本账**：水深补测或封路更正生成一版新快照，旧版置 `SUPERSEDED`、新版为 `CURRENT`，
  并逐服务点比对标出**受影响服务点**；支持补测批次（多条观测只出一版）。

> 方案说明：本实现采用题目中的“逐时精确路网最短路”，能精确表达短时开闭和绕行，代价是每个时间片重算；
> 时间依赖路径（把持续超阈路段合并为事件区间）查询量更小，但边界时刻可达性只能给区间，未采用。

### 演示路网 NET-DEMO（首次启动自动播种，6 个逐时时间片 T0–T5）

- **跨河桥 `L-BRIDGE`**：河西与河东之间唯一通道，水深 **0.35m 即封闭**；T2(0.40m)/T3(0.45m) 封闭，
  桥封闭后河东整片被孤立，T4 退水恢复。
- **两条各含一条替代路径的街道**：一号街道主街 `L-W-MAIN`（N1–N3）与经 N9 的替代环路（封闭时绕行 +100m）；
  二号街道主街 `L-E-MAIN`（N5–N6）与经 N10 的替代环路（封闭时绕行 +200m）。
- **医院急诊入口 `L-H-ENTRY`**：路段本身不按水深封闭，服务区入口另有 **0.4m** 阈值，T3(0.45m) 急诊不可用。
- **缺测支路 `L-BRANCH`（N7–N8，900 人）**：全程无水深观测，所有时间片 `UNKNOWN`，N8 始终不可达，
  直到补测补齐后才进入可达集。


## 演示数据（首次启动自动写入）

流 `FY-MAIN`（4 个分区，p0/p1/p2 为关键分区）：

| 分区 | 道路 | 初始状态 | 预期路线 |
| --- | --- | --- | --- |
| 0 | R-001 | 检查点完成，启停守恒满足 | ADAPT |
| 1 | R-002 | 快照缺失启停事件 | REPLAY（禁止零值填充） |
| 2 | R-003 | 旧作业检查点未完成；新规则 LOW→HIGH | REPLAY + 闸门待验证 |
| 3 | R-004 | 检查点完成，空启停守恒满足 | ADAPT |

## 验证方式（本仓库交付前实际执行过的验证）

```bash
# 1) 自动化测试：29 个用例全部通过（原迁移链路 17 + 路网可达性 12：
#    桥封闭前后孤立/绕行、双替代通道、缺测不当畅通、急诊 0.4m 阈值、补测/补测批次/封路更正版本账、
#    缺测有效期边界、影响图不回写迁移状态、接口鉴权与全流程）
mvn test

# 2) 端到端冒烟（应用已启动、演示数据为初始状态）：迁移链路 12 步完整升级流程，输出 SMOKE_OK
scripts/smoke.sh

# 3) 路网可达性专项冒烟：输出 ACCESS_SMOKE_OK
#    缺测支路 UNKNOWN → 桥逐时开闭与孤立/绕行 → 急诊入口阈值 → 补测批次出新版本并标出受影响服务点
#    → 封路更正再出版本 → 版本账核验 → 确认未回写迁移状态
scripts/access-smoke.sh

# 4) 重复冒烟前先重置演示数据（清空业务表并重启应用重新播种）
scripts/reset-db.sh && scripts/smoke.sh && scripts/access-smoke.sh
```

迁移冒烟流程：证明 → 迁移编排（p0/p3 适配、p1/p2 回放）→ 执行 → 双轨（p2 低→高升级）→ 闸门待验证 →
补齐 p2 检查点 → 规则二次修订 v2.1.0（证明/比较过期、计划 STALE）→ 重新绑定 → 发布新输出 →
p1 回放越过切换水印（已发布输出撤回、结果仅作候选）→ 回退（快照只读、范围撤回）→ 证据账分类统计。

可达性冒烟流程：初始快照（`L-BRANCH` 全窗口 UNKNOWN）→ 桥 T2/T3 封闭（河东孤立、河西绕行 +100m）→
医院急诊 T3 入口封闭 → 补测批次（缺测清零、N8 恢复可达、标出受影响服务点，仅一版快照）→
封路更正（再出一版）→ 版本账（仅 1 个 CURRENT，其余 SUPERSEDED）→ 确认迁移流 `FY-MAIN` 状态未被回写。

> 说明：本开发容器无 Docker 守护进程，`docker compose up --build` 未在本环境实际执行；
> Dockerfile/Compose 使用标准基础镜像（`maven:3.9-eclipse-temurin-25`、`eclipse-temurin:25-jre`、`postgres:18`），
> 应用本体已通过上述 `mvn test` 与 `scripts/smoke.sh` 在相同 jar 上验证。

## 目录结构

```
src/main/java/com/flood/statemigrate/
├── domain/      # 实体与枚举（流定义/规则包/检查点/证明/计划/双轨/闸门/回退/证据/事件
│                #   + 路网/路段/水深观测/封路更正/街区/服务区/影响快照）
├── repo/        # Spring Data JPA 仓库
├── service/     # 证明器、计划器（适配+回放）、双轨、闸门、回退、证据账、聚合与规则引擎
│   └── access/  # 路网可达性：时变通行图构建、逐时 Dijkstra 最短路、服务区影响与版本账
├── web/         # REST 控制器与请求 DTO（含 /api/access/**）
├── config/      # 安全、OpenAPI、演示数据种子（迁移链路 + 路网 NET-DEMO）
└── error/       # 统一异常处理
scripts/         # smoke.sh 迁移冒烟、access-smoke.sh 路网可达性冒烟、reset-db.sh 演示数据重置
docker/          # Maven 镜像仓库配置（构建加速）
Dockerfile · docker-compose.yml
```

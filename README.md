# 北辰 ERP 管理系统 - 项目骨架

> 屏幕行业 ERP 全链路管理系统骨架 ｜ Web 端 + 微信小程序端（小程序端待建）
> 技术栈：Spring Boot 3 + Vue 3 + MyBatis-Plus + Sa-Token，全栈可本地自建、零托管

---

## 一、项目结构

```
/workspace
├── docker-compose.yml            # 基础设施一键启动（MySQL 8 + Redis 7）
├── beichen-erp-server/           # 后端服务（Spring Boot 3 + Java 21）
│   ├── pom.xml
│   ├── sql/init.sql              # 数据库初始化脚本（建库+建表）
│   └── src/main/
│       ├── java/com/beichen/erp/
│       │   ├── ErpApplication.java          # 启动类
│       │   ├── common/                       # 统一响应体 R、分页参数
│       │   ├── config/                       # MyBatis-Plus / Sa-Token / 数据初始化
│       │   ├── exception/                    # 业务异常 + 全局异常处理
│       │   ├── auth/                         # 登录鉴权模块
│       │   └── material/                     # 物料管理模块（CRUD 示例）
│       └── resources/
│           ├── application.yml               # 后端配置
│           └── schema.sql                    # 建表脚本（启动自动执行）
│
└── beichen-erp-web/              # 前端服务（Vue 3 + TypeScript + Vite）
    ├── package.json
    ├── vite.config.ts            # 含 /api 代理到后端 8080
    └── src/
        ├── main.ts
        ├── router/               # 路由 + 登录守卫
        ├── stores/               # Pinia 状态管理（user）
        ├── utils/request.ts      # axios 封装（自动带 token）
        ├── api/                  # 接口定义（auth / material）
        ├── layout/               # 主布局（侧边栏 + 顶栏）
        └── views/                # 页面（login / dashboard / material）
```

## 二、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.5 |
| JDK | Java | 21 (LTS) |
| ORM | MyBatis-Plus | 3.5.5 |
| 鉴权 | Sa-Token | 1.37.0 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7 |
| 接口文档 | SpringDoc OpenAPI | 2.3.0 |
| 前端框架 | Vue 3 + TypeScript | 3.4 / 5.x |
| 构建工具 | Vite | 5.x |
| UI 组件库 | Element Plus | 2.6 |
| 状态管理 | Pinia | 2.x |
| HTTP 客户端 | Axios | — |

## 三、快速启动

### 前置要求
- Java 21（推荐 JDK LTS）
- Maven 3.8+
- Node.js 18+ / pnpm 8+
- Docker + Docker Compose

### 步骤 1：启动数据库（MySQL + Redis）

```bash
docker compose up -d
```

启动后：
- MySQL：`localhost:3306`，root 密码 `root`，数据库 `beichen_erp`
- Redis：`localhost:6379`

> 数据库表会在后端首次启动时通过 `schema.sql` 自动创建（CREATE TABLE IF NOT EXISTS）。

### 步骤 2：启动后端

```bash
cd beichen-erp-server
mvn spring-boot:run
```

启动成功后：
- 服务地址：http://localhost:8080
- 接口文档：http://localhost:8080/doc.html （或 /swagger-ui.html）
- 首次启动自动初始化管理员账号和示例物料数据

### 步骤 3：启动前端

```bash
cd beichen-erp-web
pnpm install
pnpm dev
```

启动成功后：
- 访问地址：http://localhost:5173
- 前端通过 Vite 代理将 `/api/**` 转发到后端 `http://localhost:8080`

### 步骤 4：登录系统

- 账号：`admin`
- 密码：`admin123`

登录后可进入「物料管理」页面进行增删改查操作。

## 四、已实现的功能

### 后端
- ✅ 统一响应体封装（`R<T>`）
- ✅ 全局异常处理（业务异常 / 未登录 / 参数校验 / 兜底）
- ✅ Sa-Token 登录鉴权 + 拦截器配置
- ✅ CORS 跨域配置
- ✅ MyBatis-Plus 分页插件 + 自动填充（createTime / updateTime）
- ✅ 登录模块（登录 / 登出 / 获取当前用户）
- ✅ 物料管理 CRUD（分页查询 + 模糊搜索 + 增删改查）
- ✅ 启动自动初始化数据（admin 用户 + 3 条示例物料）
- ✅ SpringDoc 接口文档

### 前端
- ✅ Vue 3 + TypeScript 工程化配置
- ✅ Axios 封装（请求自动带 token，401 自动跳登录）
- ✅ Pinia 用户状态管理（token 持久化到 localStorage）
- ✅ Vue Router 路由守卫（未登录拦截）
- ✅ 登录页（表单校验）
- ✅ 主布局（可折叠侧边栏 + 顶栏用户下拉）
- ✅ 物料管理页（查询 / 分页 / 新增 / 编辑 / 删除）
- ✅ 首页 Dashboard

## 五、核心接口一览

| 接口 | 方法 | 路径 | 鉴权 |
|------|------|------|------|
| 登录 | POST | `/api/auth/login` | 否 |
| 登出 | POST | `/api/auth/logout` | 是 |
| 当前用户 | GET | `/api/auth/info` | 是 |
| 物料分页 | GET | `/api/material/page` | 是 |
| 物料详情 | GET | `/api/material/{id}` | 是 |
| 新增物料 | POST | `/api/material` | 是 |
| 修改物料 | PUT | `/api/material` | 是 |
| 删除物料 | DELETE | `/api/material/{id}` | 是 |

## 六、后续开发指引

本骨架已打通「登录鉴权 → 业务 CRUD → 前后端联调」全链路，后续按业务模块横向扩展即可：

1. **新增业务模块**：参照 `material` 模块的结构（entity → mapper → service → controller），复制即可创建 BOM、采购、委外、销售等模块。
2. **新增前端页面**：在 `src/views/` 下新建页面，在 `src/router/index.ts` 注册路由，在 `src/api/` 下新增接口定义。
3. **菜单扩展**：在 `src/layout/index.vue` 的侧边栏菜单中添加新菜单项。
4. **数据库建表**：新增表结构写入 `schema.sql`（用 CREATE TABLE IF NOT EXISTS），重启后端自动建表。

### 待建模块（按技术选型方案分三期推进）
- 一期：BOM 管理、采购计划、仓储收发存、销售订单、基础财务
- 二期：委外加工、研发项目管理、文档库、成本核算
- 三期：应收应付、账单引擎、资金管理、智能体服务

## 七、默认账号与数据

| 项目 | 值 |
|------|-----|
| 管理员账号 | admin |
| 管理员密码 | admin123 |
| 示例物料 | GLASS-0601（LED玻璃原材）、FPC-0801（排线）、SCR-1001（屏幕总成） |

> ⚠️ 生产环境请务必修改默认密码，并启用更严格的密码策略。

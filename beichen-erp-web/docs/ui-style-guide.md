# 前端 UI 组件规范文档（beichen-erp-web）

> 本文档是 beichen-erp-web 前端视觉规范的唯一权威来源，所有页面开发必须遵循。
> 规范落地载体为 `src/styles/tokens.css`（Design Token），改动视觉样式应优先引用变量而非硬编码。

## 一、设计原则

- **紧凑信息密度**：ERP 后台信息量大，采用偏紧凑的字号与间距，保证一屏承载更多内容。
- **统一 Design Token**：所有字号、颜色、圆角、间距均收敛到 CSS 变量，禁止在页面散落硬编码。
- **清晰层级**：通过三级字号（正文/表格/辅助）+ 语义色建立稳定信息层级。
- **专业克制**：延续 Element Plus 默认蓝主色 `#409eff`，不做大幅视觉翻新，重点消除不一致。

## 二、Design Token 体系

定义位置：`src/styles/tokens.css`，所有变量挂在 `:root` 下，同时覆盖 Element Plus 官方 `--el-*` 变量。

### 2.1 字号阶梯（核心）

| 变量 | 值 | 用途 |
|------|-----|------|
| `--app-font-xs` | 12px | 辅助说明、占位提示、次级文字、表头次级信息 |
| `--app-font-sm` | 13px | 表格单元格、列表正文、紧凑正文 |
| `--app-font-base` | 14px | 正文、表单 label、按钮、页签标题 |
| `--app-font-md` | 15px | 小标题、区块标题 |
| `--app-font-lg` | 16px | 主标题、logo 折叠态 |
| `--app-font-xl` | 18px | 大标题、logo 展开态 |
| `--app-font-num` | 28px | 统计数字、看板大数字 |

> 特殊场景（登录页 banner 34px、登录标题 22px、错误码 96px、统计卡片数字 20px/22px）为装饰性大字号，按需保留，不强行纳入阶梯。

### 2.2 圆角阶梯

| 变量 | 值 | 用途 |
|------|-----|------|
| `--app-radius-sm` | 2px | 标签、小徽标 |
| `--app-radius-base` | 4px | 组件默认圆角（按钮、输入框、卡片） |
| `--app-radius-md` | 6px | 面板、列表项 |
| `--app-radius-lg` | 8px | 弹窗、抽屉、拖拽区 |

### 2.3 颜色体系

| 类别 | 变量 | 值 |
|------|------|-----|
| 主色 | `--app-color-primary` | `#409eff` |
| 主色 hover | `--app-color-primary-hover` | `#337ecc` |
| 成功 | `--app-color-success` | `#67c23a` |
| 警告 | `--app-color-warning` | `#e6a23c` |
| 危险 | `--app-color-danger` | `#f56c6c` |
| 信息/占位 | `--app-text-placeholder` | `#c0c4cc` |
| 主文本 | `--app-text-primary` | `#303133` |
| 常规文本 | `--app-text-regular` | `#606266` |
| 次要文本 | `--app-text-secondary` | `#909399` |
| 边框 | `--app-border-color` | `#dcdfe6` |
| 浅边框 | `--app-border-light` | `#e4e7ed` |
| 页面背景 | `--app-bg-page` | `#f0f2f5` |
| 容器背景 | `--app-bg-container` | `#ffffff` |
| 悬浮背景 | `--app-bg-hover` | `#f5f7fa` |

### 2.4 间距阶梯

| 变量 | 值 | 用途 |
|------|-----|------|
| `--app-space-xs` | 4px | 极紧凑间距 |
| `--app-space-sm` | 8px | 紧凑间距 |
| `--app-space-base` | 16px | 常规间距（页面内边距、卡片间距） |
| `--app-space-lg` | 24px | 宽松间距 |

## 三、组件使用规范

### 3.1 按钮（Button）

| 场景 | 尺寸 | 说明 |
|------|------|------|
| 主操作按钮（新增/保存/确定/查询） | `default`（默认，无需指定 size） | 表单提交、列表页主操作 |
| 表格内操作按钮 | `small` | 编辑/删除/查看等行内操作 |
| 行内输入、紧凑弹窗 | `small` | 配合表格内输入框使用 |

- 主按钮 `type="primary"`、次按钮无 type（默认）、危险操作 `type="danger"`、成功操作 `type="success"`。
- 表格内文字链接操作优先用 `link` + `size="small"`（如编辑/删除）。

### 3.2 表格（Table）

| 属性 | 规范值 |
|------|--------|
| 单元格字号 | 13px（`--app-font-sm`，由 Element Plus `--el-font-size-small` 覆盖，默认生效） |
| 表头字号 | 13px |
| 操作列按钮 | `small` |
| 表格内输入框 | `small` |
| 数值对齐 | 金额/数量列 `align="right"` |

- 数量/金额/状态用语义色：正数/正常 `--app-color-success`，负数/异常 `--app-color-danger`，预警 `--app-color-warning`。
- 空值占位统一用 `--app-text-placeholder` 的 `—` 或 `-`。

### 3.3 表单（Form）

| 属性 | 规范值 |
|------|--------|
| label 字号 | 14px（`--app-font-base`） |
| 控件尺寸 | `default`（紧凑场景 `small`） |
| 必填/校验提示 | 12px（`--app-font-xs`） |
| 辅助说明文字 | 12px + `--app-text-secondary` |

### 3.4 弹窗（Dialog / Drawer）

| 属性 | 规范值 |
|------|--------|
| 标题字号 | 16px（Element Plus 默认） |
| 内容字号 | 14px（`--app-font-base`） |
| 底部按钮组 | `default` |
| 圆角 | `--app-radius-lg`（8px） |
| 移动端宽度 | `92vw`（见 `index.css` 媒体查询） |

### 3.5 卡片 / 页签

- 卡片阴影 `shadow="never"`，间距 `--app-space-base`（16px）。
- 页签标题 14px，激活色 `--app-color-primary`。

## 四、开发约束（强制）

1. **禁止硬编码**：页面中不得出现 `font-size:12px`、`color:#409eff`、`color:#f56c6c` 等硬编码值，一律改用 `var(--app-*)` 变量或语义工具类。
2. **语义工具类**：`tokens.css` 提供了 `.text-aux`（12px 辅助）、`.text-muted`（12px 占位）、`.text-table`（13px 表格）、`.text-subtitle`（15px 小标题）、`.text-primary`（主色文字），可复用。
3. **新变量先定义**：需要新语义色/字号时，先在 `tokens.css` 的 `:root` 中定义 `--app-*` 变量，再引用。
4. **不修改功能逻辑**：样式规范化仅调整视觉呈现，禁止触碰业务逻辑、数据字段、状态流转。
5. **提交前自检**：新增/修改页面后，`pnpm exec vite build` 验证构建通过；涉及 `.vue` 内联样式的，确认无 `#` 硬编码颜色残留。

## 五、规范落地说明

- **入口顺序**：`main.ts` 中引入顺序为 `element-plus/dist/index.css` → `styles/tokens.css` → `styles/index.css`，确保 `--el-*` 覆盖优先级正确。
- **Element Plus 覆盖**：`tokens.css` 已覆盖 `--el-color-primary`、`--el-font-size-*`、`--el-border-radius-*`、`--el-text-color-*`、`--el-border-color` 等，组件默认即符合规范。

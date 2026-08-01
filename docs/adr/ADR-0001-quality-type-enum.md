# ADR-0001: 品质等级枚举设计

- **状态**: 已采纳
- **日期**: 2026-07-31

## 背景

成品存在品质差异——同一产品按质检结果分为 A 规（优等）、B 规（合格）、C 规（次品）、不良（缺陷品），各等级库存需独立管理，不可混合。

## 决策

使用 Java 枚举 `ProductQualityType` 统一管理品质等级：

```java
public enum ProductQualityType {
    A("A规"),
    B("B规"),
    C("C规"),
    DEFECT("不良");
}
```

- DB 列 `quality_type VARCHAR(10)` 存储枚举 name（A/B/C/DEFECT）
- 前端通过 `GET /api/product/quality-types` 接口动态获取选项列表，不硬编码
- 品质列默认值统一为 "A"（A 规）

## 后果

- 枚举值稳定，4 种等级不会轻易变化
- 前端无需硬编码，新增等级只需修改枚举并重启后端
- 所有明细表和库存表 quality_type 列默认值 `'A'`，与 `changeStock` null 兜底一致

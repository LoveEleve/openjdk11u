# 01-基础设施 — 新增 Optional CSet 基础设施

## 概述

在 JDK 11 G1 的基础上新增 Optional 状态，为后续 Abortable Mixed GC 提供基础能力。
本阶段所有文件**无依赖**，可独立编译。

## 文件清单

| 文件 | 改动类型 | 改动量 |
|------|----------|:------:|
| `g1InCSetState.hpp` | 修改 | +9/-1 |
| `workerDataArray.hpp` | 修改 | +2/-0 |
| `workerDataArray.inline.hpp` | 修改 | +11/-0 |
| `heapRegion.hpp` | 修改 | +6/-0 |
| `heapRegion.cpp` | 修改 | +2/-0 |
| **合计** | | **+30/-1** |

## 各文件详情

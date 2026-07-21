# 02-核心数据结构 — G1OptionalCSet + Optional Region 管理

## 概述

这是 Abortable Mixed GC 的核心。引入 `G1OptionalCSet` 类负责 optional region 的
运行时管理（准备/执行/中止/收尾），以及 `G1OopStarChunkedList` 负责推迟扫描。

## 文件清单

| 文件 | 改动类型 | 改动量 |
|------|----------|:------:|
| `g1OopStarChunkedList.hpp` | **新增** | +64 |
| `g1OopStarChunkedList.inline.hpp` | **新增** | +84 |
| `g1OopStarChunkedList.cpp` | **新增** | +40 |
| `g1CollectionSet.hpp` | 修改 | +85/-1 |
| `g1CollectionSet.cpp` | 修改 | +251/-75 |
| `g1Policy.hpp` | 修改 | +8/-0 |
| `g1CollectedHeap.hpp` | 修改 | +12/-0 |
| `g1CollectedHeap.cpp` | 修改 | +90/-2 |
| **合计** | | **+634/-78** |

## 各文件详情

# G1 Abortable Mixed GC — JEP 344 Backport

## 概述

将 JDK 12 引入的 **G1 Abortable Mixed Collections (JEP 344)** 移植到 JDK 11u。

**要解决的问题**：G1 Mixed GC 一次性回收所有 old region 时可能超过 `-XX:MaxGCPauseMillis`，
导致暂停时间不可控。

**解决方案**：将 old region 分成"必回收"和"可选"两类。可选 region 在 GC 暂停中按时间
预算逐批回收，超时即中止，剩余 region 留到下次 GC 继续。

## 子补丁结构

```
patch/
├── README.md                    ← 本文件
│
├── 01-infrastructure/           ← 基础设施层
│   ├── README.md                ← 概览
│   ├── g1InCSetState.md         ← 新增 Optional 状态
│   ├── workerDataArray.md       ← 线程统计数组扩容
│   └── heapRegion.md            ← 新增 _index_in_opt_cset 字段
│
├── 02-core-collection-set/      ← 核心数据结构
│   ├── README.md                ← 概览
│   ├── g1OopStarChunkedList.md  ← 推迟扫描链表（新文件）
│   ├── g1CollectionSet.md       ← G1OptionalCSet + optional_region 数组
│   ├── g1Policy.md              ← 时间预算 fraction 常量
│   └── g1CollectedHeap.md       ← evacuate_optional_collection_set
│
└── 03-runtime-closures/         ← 运行时 + 闭包适配
    ├── README.md                ← 概览
    ├── g1ParScanThreadState.md  ← 推迟扫描 + remember_root
    ├── g1GCPhaseTimes.md        ← 新增 Optional 阶段统计
    ├── g1OopClosures.md         ← Optional 闭包
    └── g1RemSet.md              ← RSet 扫描适配
```

## 改动统计

| 阶段 | 文件数 | 新增行 | 删除行 |
|------|:------:|:------:|:------:|
| 01-基础设施 | 5 | +22 | -1 |
| 02-核心数据结构 | 7 | +634 | -78 |
| 03-运行时闭包 | 6 | +151 | -11 |
| **总计** | **18** | **+807** | **-90** |

## 核心流程

```
do_collection_pause_at_safepoint:
  │
  ├── finalize_old_part()          ← 按时间预算把 old region 分成两档
  │     ├── 时间够 → add_as_old()  → 保证 CSet（必回收）
  │     └── 时间紧 → add_as_optional() → optional 数组（有时间就做）
  │
  ├── evacuate_collection_set()    ← 正常 young + 保证的 old
  │     └── root scan 遇到 Optional region → push 到 G1OopStarChunkedList（推迟）
  │
  ├── evacuate_optional_collection_set()  ← 新增 do-while 循环
  │     ├── 计算 time_left = MaxGCPauseMillis - elapsed
  │     ├── prepare_evacuation(time_left * 0.75) ← 拿一批 region
  │     ├── 超时 → break（中止！）
  │     └── evacuate → 消费 G1OopStarChunkedList → complete
  │
  └── ~G1OptionalCSet: 未处理的 optional region 放回 old set + chooser
```

## 中止条件

1. `time_left_ms < 0` — 已超过 MaxGCPauseMillis，全部跳过
2. `prepare_failed()` — 剩余时间连一个 optional region 都不够
3. `evacuation_failed()` — 有 region 发生晋升失败

## 分支信息

- **分支**：`feat/g1-abortable-mixed-gc`
- **基准**：`origin/main`
- **提交**：
  - `73a7102` Stage 1: infrastructure
  - `cf7a034` Stage 2-3: core implementation

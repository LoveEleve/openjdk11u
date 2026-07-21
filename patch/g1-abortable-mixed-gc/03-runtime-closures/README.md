# 03-运行时闭包 — Optional 闭包 + Phase 统计 + RSet 适配

## 概述

为 Abortable Mixed GC 的运行时层提供闭包适配、时间统计和 RSet 扫描支持。

## 文件清单

| 文件 | 改动类型 | 改动量 |
|------|----------|:------:|
| `g1ParScanThreadState.hpp` | 修改 | +23/-1 |
| `g1ParScanThreadState.inline.hpp` | 修改 | +29/-0 |
| `g1ParScanThreadState.cpp` | 修改 | +35/-2 |
| `g1GCPhaseTimes.hpp` | 修改 | +24/-0 |
| `g1GCPhaseTimes.cpp` | 修改 | +39/-10 |
| `g1OopClosures.hpp` | 修改 | +11/-0 |
| `g1OopClosures.inline.hpp` | 修改 | +10/-0 |
| `g1RemSet.hpp` | 修改 | +4/-0 |
| `g1RemSet.cpp` | 修改 | +4/-2 |
| **合计** | | **+179/-15** |

## 各文件详情

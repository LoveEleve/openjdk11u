# G1RemSet — Optional RSet 扫描适配

## 文件
- `src/hotspot/share/gc/g1/g1RemSet.hpp`
- `src/hotspot/share/gc/g1/g1RemSet.cpp`

## 改动内容

### 1. `G1ScanRSForRegionClosure` 新增 phase 字段

```cpp
// .hpp: 新增
G1GCPhaseTimes::GCParPhases _phase;

// 构造函数新增 phase 参数：
G1ScanRSForRegionClosure(G1RemSetScanState* scan_state,
                         G1ScanObjsDuringScanRSClosure* scan_obj_on_card,
                         G1ParScanThreadState* pss,
                         G1GCPhaseTimes::GCParPhases phase,   // 新增
                         uint worker_i);

// 构造函数初始化列表新增：
_phase(phase),

// .cpp 调用点：
// Optional RSet 扫描时传 G1GCPhaseTimes::OptScanRS：
G1ScanRSForRegionClosure scan_rs_cl(scan_state, &obj_cl, pss,
                                     G1GCPhaseTimes::OptScanRS, worker_id);

// 普通 RSet 扫描时传 G1GCPhaseTimes::ScanRS（原有行为变为显式传参）：
G1ScanRSForRegionClosure cl(_scan_state, &scan_cl, pss,
                             G1GCPhaseTimes::ScanRS, worker_i);
```

### 2. 新增 include

```cpp
#include "gc/g1/g1GCPhaseTimes.hpp"  // 需要 G1GCPhaseTimes::GCParPhases 枚举
```

## 设计要点

- **为什么同一个类可以用于普通和 optional 两种扫描？** 通过 `phase` 参数区分。
  G1ScanRSForRegionClosure 本身不关心是哪种场景，只是把 phase 传递给统计系统
  （`record_thread_work_item` 等方法自动按 phase 分桶存储）
- **OptScanRS 的统计数据去哪了？** 在 `G1GCPhaseTimes::print()` 中通过
  `print_evacuate_optional_collection_set()` 单独输出，和普通 ScanRS 区分开

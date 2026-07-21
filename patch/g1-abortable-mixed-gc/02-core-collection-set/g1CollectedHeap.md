# G1CollectedHeap — evacuate_optional_collection_set

## 文件
- `src/hotspot/share/gc/g1/g1CollectedHeap.hpp`
- `src/hotspot/share/gc/g1/g1CollectedHeap.cpp`

## 改动内容

### 1. .hpp 新增

```cpp
// 注册 optional region 到 fast test
void register_optional_region_with_cset(HeapRegion* r);

// 新增两个方法声明
void evacuate_optional_collection_set(G1ParScanThreadStateSet* pss);
void evacuate_optional_regions(G1ParScanThreadStateSet* pss, G1OptionalCSet* ocset);

// G1ParEvacuateFollowersClosure 新增 phase 字段
G1GCPhaseTimes::GCParPhases _phase;
```

### 2. .cpp — 新增类 `G1EvacuateOptionalRegionTask`

并行任务，负责批量 evacuate optional regions：

```
work(uint worker_id):
  ├── scan_roots(pss, worker_id)
  │     └── 遍历 [current_index, current_limit) 的 optional region
  │         ├── 消费 G1OopStarChunkedList（推迟的 root oop）
  │         ├── 扫描 RSet（optional 的 cards）
  │         └── 记录 scanned/claimed/skipped/memory 统计
  │
  └── evacuate_live_objects(pss, worker_id)
        └── G1ParEvacuateFollowersClosure 并行 evacuate
```

### 3. .cpp — `evacuate_optional_collection_set()`

**核心 do-while 循环**（这是 Abortable Mixed GC 的本质）：

```cpp
void G1CollectedHeap::evacuate_optional_collection_set(pss) {
    G1OptionalCSet optional_cset(&_collection_set, pss);
    if (optional_cset.is_empty() || evacuation_failed()) return;

    double gc_start = phase_times->cur_collection_start_sec() * 1000.0;
    double start = os::elapsedTime();

    do {
        double time_used = os::elapsedTime() * 1000.0 - gc_start;
        double time_left = MaxGCPauseMillis - time_used;

        if (time_left < 0) break;                              // 超时！中止

        optional_cset.prepare_evacuation(
            time_left * policy->optional_evacuation_fraction()); // 0.75 的剩余时间

        if (optional_cset.prepare_failed()) break;             // 时间不够做一个 region

        evacuate_optional_regions(pss, &optional_cset);         // 并行处理
        optional_cset.complete_evacuation();

        if (optional_cset.evacuation_failed()) break;          // 晋升失败

    } while (!optional_cset.is_empty());

    phase_times->record_optional_evacuation(
        (os::elapsedTime() - start) * 1000.0);
}
```

### 4. .cpp — 调用点改动

```cpp
// do_collection_pause_at_safepoint() 中：
// 原来：
G1ParScanThreadStateSet per_thread_states(this, workers, young_len);

// 改为（多传 optional_region_length）：
G1ParScanThreadStateSet per_thread_states(this, workers, young_len, optional_len);

// 原来只调 evacuate_collection_set：
evacuate_collection_set(&per_thread_states);

// 改为先 normal 再 optional：
evacuate_collection_set(&per_thread_states);
evacuate_optional_collection_set(&per_thread_states);  // 新增
```

### 5. G1ParEvacuateFollowersClosure 调用点改动

```cpp
// 原来：
G1ParEvacuateFollowersClosure evac(g1h, pss, queues, &terminator);

// 改为（多传 phase 追踪）：
G1ParEvacuateFollowersClosure evac(g1h, pss, queues, &terminator, G1GCPhaseTimes::ObjCopy);
//                                                              ^^^^^^^^^^^^^^^^^^^^^^^^
//                               Optional evacuation 时传 G1GCPhaseTimes::OptObjCopy
```

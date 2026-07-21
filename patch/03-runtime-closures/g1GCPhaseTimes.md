# G1GCPhaseTimes — 新增 Optional 阶段统计

## 文件
- `src/hotspot/share/gc/g1/g1GCPhaseTimes.hpp`
- `src/hotspot/share/gc/g1/g1GCPhaseTimes.cpp`

## 改动内容

### 1. 新增 GC 阶段枚举

```cpp
// 并行阶段新增两个 phase：
enum GCParPhases {
    ...
    ScanRS,
    OptScanRS,     // 新增：Optional RSet 扫描
    ...
    ObjCopy,
    OptObjCopy,    // 新增：Optional 对象拷贝
    ...
};
```

### 2. 新增 work items 枚举

```cpp
enum GCOptCSetWorkItems {
    OptCSetScannedCards,   // 扫描的卡片数
    OptCSetClaimedCards,   // 认领的卡片数
    OptCSetSkippedCards,   // 跳过的卡片数
    OptCSetUsedMemory      // 推迟链表内存开销（新增，需要 workerDataArray slot 4）
};
```

### 3. 新增统计字段

```cpp
WorkerDataArray<size_t>* _opt_cset_scanned_cards;
WorkerDataArray<size_t>* _opt_cset_claimed_cards;
WorkerDataArray<size_t>* _opt_cset_skipped_cards;
WorkerDataArray<size_t>* _opt_cset_used_memory;
double _cur_optional_evac_ms;  // 本轮 optional evacuation 总耗时
```

### 4. 新增方法

```cpp
// 通用版 record_or_add（替代原来硬编码 ObjCopy 的版本）
void record_or_add_time_secs(GCParPhases phase, uint worker_i, double secs);

// 线程工作项追加
void record_or_add_thread_work_item(GCParPhases phase, uint worker_i,
                                     size_t count, uint index);

// 记录 optional evacuation 总时间
void record_optional_evacuation(double ms);

// 打印 optional evacuation 统计
double print_evacuate_optional_collection_set() const;
```

### 5. 构造函数初始化

```cpp
// 为 OptScanRS 和 OptObjCopy 创建 WorkerDataArray
_gc_par_phases[OptScanRS] = new WorkerDataArray<double>(max_gc_threads, "Optional Scan RS (ms):");
_gc_par_phases[OptObjCopy] = new WorkerDataArray<double>(max_gc_threads, "Optional Object Copy (ms):");

// 链接 work items（每个 phase 下挂多个子统计）
_gc_par_phases[OptScanRS]->link_thread_work_items(_opt_cset_scanned_cards, OptCSetScannedCards);
_gc_par_phases[OptScanRS]->link_thread_work_items(_opt_cset_claimed_cards, OptCSetClaimedCards);
_gc_par_phases[OptScanRS]->link_thread_work_items(_opt_cset_skipped_cards, OptCSetSkippedCards);
_gc_par_phases[OptScanRS]->link_thread_work_items(_opt_cset_used_memory, OptCSetUsedMemory);
```

### 6. record_or_add_time_secs() 通用化

```cpp
// 原来：record_or_add_objcopy_time_secs() 只支持 ObjCopy
// 现在：record_or_add_time_secs(phase, worker, secs) 支持任意 phase
// first call → set(), subsequent calls → add()
void G1GCPhaseTimes::record_or_add_time_secs(GCParPhases phase, uint worker_i, double secs) {
    if (_gc_par_phases[phase]->get(worker_i) == _gc_par_phases[phase]->uninitialized()) {
        record_time_secs(phase, worker_i, secs);
    } else {
        add_time_secs(phase, worker_i, secs);
    }
}
```

### 7. reset() 新增

```cpp
_cur_optional_evac_ms = 0.0;
```

### 8. print() 流程新增

```cpp
void G1GCPhaseTimes::print() {
    accounted_ms += print_pre_evacuate_collection_set();
    accounted_ms += print_evacuate_collection_set();
    accounted_ms += print_evacuate_optional_collection_set();  // 新增
    accounted_ms += print_post_evacuate_collection_set();
}
```

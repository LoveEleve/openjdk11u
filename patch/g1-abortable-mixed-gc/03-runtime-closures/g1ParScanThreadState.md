# G1ParScanThreadState — 推迟扫描 + Remember Root

## 文件
- `src/hotspot/share/gc/g1/g1ParScanThreadState.hpp`
- `src/hotspot/share/gc/g1/g1ParScanThreadState.inline.hpp`
- `src/hotspot/share/gc/g1/g1ParScanThreadState.cpp`

## 改动内容

### 1. 新增字段

```cpp
// .hpp:91-92
size_t _num_optional_regions;                         // optional region 数量
G1OopStarChunkedList* _oops_into_optional_regions;    // per-region 的推迟链表数组
```

### 2. 构造函数改动

```cpp
// 原签名：
G1ParScanThreadState(G1CollectedHeap* g1h, uint worker_id, size_t young_cset_length);

// 新签名（多传 optional_cset_length）：
G1ParScanThreadState(G1CollectedHeap* g1h, uint worker_id,
                     size_t young_cset_length, size_t optional_cset_length)

// 新增初始化：
_oops_into_optional_regions = new G1OopStarChunkedList[_num_optional_regions];
```

### 3. 新增推迟方法（.inline.hpp）

```cpp
// Root 扫描时：指向 optional region 的引用推迟存储
template <typename T>
inline void remember_root_into_optional_region(T* p) {
    oop o = RawAccess<IS_NOT_NULL>::oop_load(p);
    uint index = _g1h->heap_region_containing(o)->index_in_opt_cset();
    _oops_into_optional_regions[index].push_root(p);  // 按 region 索引分桶
}

// 扫描对象字段时：指向 optional region 的引用推迟存储
template <typename T>
inline void remember_reference_into_optional_region(T* p) {
    oop o = RawAccess<IS_NOT_NULL>::oop_load(p);
    uint index = _g1h->heap_region_containing(o)->index_in_opt_cset();
    _oops_into_optional_regions[index].push_oop(p);   // 按 region 索引分桶
}

// 获取指定 region 的推迟链表（optional evacuation 时消费）
inline G1OopStarChunkedList* oops_into_optional_region(const HeapRegion* hr) {
    return &_oops_into_optional_regions[hr->index_in_opt_cset()];
}
```

### 4. 析构函数

```cpp
delete[] _oops_into_optional_regions;
```

### 5. G1ParScanThreadStateSet 新增方法

```cpp
// 记录未回收的 optional region 的内存开销
void record_unused_optional_region(HeapRegion* hr) {
    for each worker:
        size_t used_memory = pss->oops_into_optional_region(hr)->used_memory();
        phase_times->record_or_add_thread_work_item(OptScanRS, worker, used_memory, OptCSetUsedMemory);
}
```

## 推迟扫描流

```
root scan 阶段:
  G1ParCopyClosure::do_oop_work(p)
    → state.is_optional()
    → pss->remember_root_into_optional_region(p)
    → oops_into_optional_regions[index].push_root(p)   ← 推迟

optional evacuation 阶段:
  G1EvacuateOptionalRegionTask::scan_roots()
    → pss->oops_into_optional_region(hr)
    → oops_do(scan_cl, root_cl)                        ← 消费
```

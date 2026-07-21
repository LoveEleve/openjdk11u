# workerDataArray — 线程统计数组扩容

## 文件
- `src/hotspot/share/gc/shared/workerDataArray.hpp`
- `src/hotspot/share/gc/shared/workerDataArray.inline.hpp`

## 改动内容

### 1. `MaxThreadWorkItems` 3 → 4

```cpp
// .hpp line 37
static const uint MaxThreadWorkItems = 4;  // 原值 3
```

**原因**：Optional CSet 的 phase 统计需要第 4 个 work item slot（`G1GCPhaseTimes::GCOptCSetWorkItems`）。
原来 3 个 slot 分别用于 ScannedCards / ClaimedCards / SkippedCards，现在需要多一个
OptCSetUsedMemory 用于记录推迟扫描的内存开销。

### 2. 新增 `set_or_add_thread_work_item()` 方法

```cpp
// .hpp line 52: 声明
void set_or_add_thread_work_item(uint worker_i, size_t value, uint index = 0);

// .inline.hpp line 83-92: 实现
template <typename T>
void WorkerDataArray<T>::set_or_add_thread_work_item(uint worker_i, size_t value, uint index) {
  assert(index < MaxThreadWorkItems, "...");
  assert(_thread_work_items[index] != NULL, "No sub count");
  if (_thread_work_items[index]->get(worker_i) == uninitialized()) {
    _thread_work_items[index]->set(worker_i, value);      // 首次写入
  } else {
    _thread_work_items[index]->add(worker_i, value);      // 后续追加
  }
}
```

**原因**：Optional evacuation 是多轮 do-while 循环。同一 worker 可能参与多轮，
统计值需要**累加**而非覆盖。`set_or_add` 比原生的 `set` 更适合多轮场景。

## 影响
- `G1GCPhaseTimes` 的 Optional phase 统计可以跨多轮 evacuate 累加
- 不会因为第二轮调用 `set()` 而覆盖第一轮统计

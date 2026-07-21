# heapRegion — 新增 `_index_in_opt_cset` 字段

## 文件
- `src/hotspot/share/gc/g1/heapRegion.hpp`
- `src/hotspot/share/gc/g1/heapRegion.cpp`

## 改动内容

### 1. 新增字段

```cpp
// heapRegion.hpp:252
uint _index_in_opt_cset;
```

这个字段记录该 region 在 `G1CollectionSet._optional_regions` 数组中的索引。
未进入 optional 数组时为 `UINT_MAX`。

### 2. 新增 getter/setter

```cpp
// heapRegion.hpp:556-557
uint index_in_opt_cset() const { return _index_in_opt_cset; }
void set_index_in_opt_cset(uint index) { _index_in_opt_cset = index; }
```

### 3. 构造函数初始化

```cpp
// heapRegion.cpp:242
_index_in_opt_cset(UINT_MAX),    // 初始值：不在 optional 数组中
```

### 4. 新增 include

```cpp
// heapRegion.cpp:29
#include "gc/g1/g1CollectionSet.hpp"
```

**原因**：后续需要引用 `G1OptionalCSet::InvalidCSetIndex` 常量（值为 `UINT_MAX`）。

## 设计要点

- **为什么用 UINT_MAX 表示"不在数组中"？** 和 `_young_index_in_cset` 的 -1 同样语义：哨兵值
- **什么时候被赋值？** `G1CollectionSet::add_optional_region()` 将 region 加入
  optional 数组时，设置其 `index_in_opt_cset`
- **谁在读取？** `G1ParScanThreadState::remember_root_into_optional_region()` 通过
  这个索引找到对应的 `G1OopStarChunkedList` 实例

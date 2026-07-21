# G1OopStarChunkedList — 推迟扫描链表

## 文件（3 个新文件，共 188 行）
- `g1OopStarChunkedList.hpp` (64 行)
- `g1OopStarChunkedList.inline.hpp` (84 行)
- `g1OopStarChunkedList.cpp` (40 行)

## 为什么需要

**问题**：root scan 时遇到指向 optional region 中对象的引用，应该怎么办？

| 方案 | 问题 |
|------|------|
| 立刻 evacuate | region 最终可能被中止，白费功夫 |
| 直接跳过 | region 被选中时需要重新扫 root，太慢 |
| **推迟记录** ✓ | 先记下来，region 确认被选中后消费 |

这就是 G1OopStarChunkedList 要做的：root scan 时暂存指向 optional region 的指针，
等 region 确认会被回收时再遍历这些指针完成 evacuate。

## 核心设计

### 四种链表

```cpp
ChunkedList<oop*, mtGC>*      _roots;   // 来自 root 的普通指针
ChunkedList<narrowOop*, mtGC>* _croots;  // 来自 root 的压缩指针
ChunkedList<oop*, mtGC>*      _oops;    // 来自引用字段的普通指针
ChunkedList<narrowOop*, mtGC>* _coops;  // 来自引用字段的压缩指针
```

分四类是为了适配 GC 的 oop/narrowOop 双指针体系。

### ChunkedList 基础

`ChunkedList<T, mtGC>` 是已有的模板（`utilities/chunkedList.hpp`, 81 行）：
- 每个 chunk 固定 64 个元素（`BufferSize = 64`）
- 满了自动分配新 chunk 链式串联
- 支持 `push()`, `at()`, `size()`, `next_used()`, `set_next_used()`

### 写入 API

```cpp
// root scan 检测到 Optional region：
inline void push_root(oop* p)      { push(&_roots, p); }
inline void push_root(narrowOop* p) { push(&_croots, p); }

// 引用字段指向 Optional region：
inline void push_oop(oop* p)       { push(&_oops, p); }
inline void push_oop(narrowOop* p)  { push(&_coops, p); }
```

### 消费 API

```cpp
// Optional region 被确认选中后，消费所有推迟的指针
void oops_do(OopClosure* obj_cl, OopClosure* root_cl) {
    chunks_do(_roots, root_cl);   // 遍历 root 指针，调 root_cl->do_oop(p)
    chunks_do(_croots, root_cl);
    chunks_do(_oops, obj_cl);     // 遍历普通引用，调 obj_cl->do_oop(p)
    chunks_do(_coops, obj_cl);
}
```

### 内存追踪

```cpp
size_t _used_memory;  // 累计分配的 ChunkedList 对象大小
size_t used_memory() { return _used_memory; }
```

GC 结束后统计所有 worker 的 `used_memory`，记录到 `G1GCPhaseTimes`。

## 生命周期

```
root scan 阶段:
  G1ParCopyClosure::do_oop_work(p)
    → state.is_optional()
    → remember_root_into_optional_region(p)
    → push_root(p)  ← 写入 chunked list

optional evacuation 阶段:
  G1EvacuateOptionalRegionTask::scan_roots()
    → oops_into_optional_region(hr)->oops_do(...)
    → chunks_do() ← 消费全部推迟指针

析构：
  ~G1OopStarChunkedList()
    → delete_list() 逐 chunk 释放
```

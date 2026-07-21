# G1CollectionSet — G1OptionalCSet + Optional Region 数组

## 文件
- `src/hotspot/share/gc/g1/g1CollectionSet.hpp`
- `src/hotspot/share/gc/g1/g1CollectionSet.cpp`

## 改动一览（+336/-76，最大改动文件）

### 1. 新增 optional_region 数组字段

```cpp
// g1CollectionSet.hpp:60-62
HeapRegion** _optional_regions;          // optional region 数组
uint         _optional_region_length;    // 当前个数
uint         _optional_region_max_length; // 数组容量
```

### 2. 新增管理方法

```cpp
// 初始化/销毁
void initialize_optional(uint max_length);
void free_optional_regions();

// 添加 optional region
void add_optional_region(HeapRegion* hr);
// 移除最后一个（中止时用）
HeapRegion* remove_last_optional_region();
// 按索引获取
HeapRegion* optional_region_at(uint i) const;
// 清除指定索引的 region
void clear_optional_region(uint index);
```

### 3. finalize_old_part() 重写

**原逻辑**：所有 old region 统一加入 CSet

**新逻辑**：
```
时间预算 = time_remaining_ms * 0.2  (来自 g1Policy::optional_prediction_fraction)

for each old region:
    if 已达 max_old_cset_length: break

    if 已回收足够: break

    if old_count < min_old_cset_length:
        add_as_old(hr)              ← 必回收
    elif 预测时间 <= 时间预算:
        add_as_old(hr)              ← 仍有时间，必回收
    elif 预测时间 <= time_remaining:
        add_as_optional(hr)         ← 有时间但不多了，可选回收
    else:
        break                       ← 没时间了
```

### 4. G1OptionalCSet 类（内嵌 +71 行）

```cpp
class G1OptionalCSet : public StackObj {
  G1CollectionSet*          _cset;
  G1ParScanThreadStateSet*  _pset;
  uint _current_index;    // 已完成 evacuate 的第一个 region
  uint _current_limit;    // 下一次 prepare 时的第一个 region
  bool _prepare_failed;   // 本轮 prepare 是否失败
  bool _evacuation_failed; // 本轮是否发生晋升失败
  
public:
  static const uint InvalidCSetIndex = UINT_MAX;
  
  bool is_empty() const;
  uint size() const;
  uint current_index() const;
  uint current_limit() const;
  bool prepare_failed() const;
  bool evacuation_failed() const;
  HeapRegion* region_at(uint i) const;
  
  void prepare_evacuation(double time_left_ms);
  void complete_evacuation();
  
  ~G1OptionalCSet();  // 中止时把未处理 region 放回 old set + chooser
};
```

### 5. 析构函数关键逻辑

GC 暂停结束时，`G1OptionalCSet` 自动析构，未处理的 optional region：

```cpp
~G1OptionalCSet() {
    while (!is_empty()) {
        hr = remove_last_optional_region();  // 逆序取出
        g1h->old_set_add(hr);                // 放回 old set
        g1h->clear_in_cset(hr);              // 清除 in_cset 标记
        hr->set_index_in_opt_cset(InvalidCSetIndex);
        cset_chooser()->push(hr);            // 推回 chooser 前端（下次优先）
    }
    free_optional_regions();
}
```

**逆序取出的原因**：保持 chooser 中的回收效率排序（高回收率的 region 在前）。

## 操作顺序总览

```
准备阶段:
  initialize_optional(max_length)
  finalize_old_part() → add_as_old / add_as_optional
  
执行阶段:
  while !is_empty():
    prepare_evacuation(time_left * 0.75)  ← 选一批
    并行 evacuate
    complete_evacuation()                 ← 检查失败
    
收尾:
  ~G1OptionalCSet() → 归还未处理 region
  free_optional_regions()
```

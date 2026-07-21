# g1InCSetState — 新增 Optional 状态

## 文件
`src/hotspot/share/gc/g1/g1InCSetState.hpp`

## 改动内容

### 1. 新增 `Optional = -2` 枚举值

```cpp
// line 60-64
enum {
    Humongous    = -1,    // 大对象 region
    Optional     = -2,    // 可选回收 region（新增）
    NotInCSet    =  0,    // 不在 CSet 中
    Young        =  1,    // 年轻代
    Old          =  2,    // 老年代
    Num
};
```

### 2. 新增 `is_optional()` 判断方法

```cpp
// line 79
bool is_optional() const { return _value == Optional; }
```

### 3. 更新 `is_valid()` 检查范围

```cpp
// line 83: 下限从 Humongous(-1) 扩展到 Optional(-2)
bool is_valid() const { return (_value >= Optional) && (_value < Num); }
```

### 4. 新增 `set_optional()` 快速标记方法

```cpp
// line 132-136
void set_optional(uintptr_t index) {
    assert(get_by_index(index).is_default(),
           "State at index should be default");
    set_by_index(index, InCSetState::Optional);
}
```

## 设计要点

- **为什么是 -2？** `is_in_cset()` 用 `_value > NotInCSet` 判断（即 >0），Optional 为负值，不走正常 CSet 路径，但 `is_optional()` 单独判断
- **和 Humongous 的区别**：Humongous 是"急切回收"，Optional 是"延迟回收"

## 影响
- Root 扫描时检测到 Optional region 不立即 evacuate，推迟到 G1OopStarChunkedList
- `is_valid()` 范围扩展，确保 Optional 状态不触发 assert

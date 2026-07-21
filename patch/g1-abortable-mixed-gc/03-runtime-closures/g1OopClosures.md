# G1OopClosures — 新增 Optional 闭包 + 状态检测

## 文件
- `src/hotspot/share/gc/g1/g1OopClosures.hpp`
- `src/hotspot/share/gc/g1/g1OopClosures.inline.hpp`

## 改动内容

### 1. 新增 `G1ScanRSForOptionalClosure`

```cpp
// 用于 Optional RSet 扫描的闭包，确保及时 trim 队列
class G1ScanRSForOptionalClosure : public OopClosure {
    G1ScanObjsDuringScanRSClosure* _scan_cl;
public:
    G1ScanRSForOptionalClosure(G1ScanObjsDuringScanRSClosure* cl) : _scan_cl(cl) { }
    template <class T> void do_oop_work(T* p);
    virtual void do_oop(oop* p)          { do_oop_work(p); }
    virtual void do_oop(narrowOop* p)    { do_oop_work(p); }
};

// 实现：和普通 ScanRS 一样处理，但额外做 queue 的 partial trim
template <class T>
inline void G1ScanRSForOptionalClosure::do_oop_work(T* p) {
    _scan_cl->do_oop_work(p);
    _scan_cl->trim_queue_partially();  // 关键：及时清空局部队列
}
```

### 2. `G1ScanClosureBase::handle_non_cset_obj_common()` 新增分支

```cpp
template <class T>
inline void G1ScanClosureBase::handle_non_cset_obj_common(InCSetState state, T* p, oop obj) {
    if (state.is_humongous()) {
        _g1h->set_humongous_is_live(obj);
    } else if (state.is_optional()) {                              // 新增
        _par_scan_state->remember_reference_into_optional_region(p); // 推迟扫描
    }
}
```

### 3. `G1ParCopyClosure::do_oop_work()` 新增分支

```cpp
// 对象不在 CSet 中时的处理：
if (state.is_humongous()) {
    _g1h->set_humongous_is_live(obj);
} else if (state.is_optional()) {                              // 新增
    _par_scan_state->remember_root_into_optional_region(p);      // 推迟 root 扫描
}
```

## 总结流程

```
对象引用解析:
  state = inCSetState(obj)
  
  if state.is_in_cset():
      正常 evacuate
  elif state.is_humongous():
      标记为 live
  elif state.is_optional():              ← 新增分支
      push 到 G1OopStarChunkedList       ← 推迟处理
  else:
      正常 dirty card / update RSet
```

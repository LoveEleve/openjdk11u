# G1Policy — 新增时间预算 Fraction 常量

## 文件
`src/hotspot/share/gc/g1/g1Policy.hpp`

## 改动内容

```cpp
// line 404-409
// 预测阶段：20% 的时间预算给 optional regions
double optional_prediction_fraction() { return 0.2; }

// 执行阶段：75% 的剩余时间用来决定当前这批拿多少 optional regions
double optional_evacuation_fraction() { return 0.75; }
```

## 两阶段时间预算

| 阶段 | Fraction | 含义 |
|------|:------:|------|
| **预测阶段** (`finalize_old_part`) | 0.2 | 剩余时间的 20% 用来决定哪些 old region 进入 optional 数组 |
| **执行阶段** (`evacuate_optional`) | 0.75 | 剩余时间的 75% 用来决定当前这批 evacuate 多少个 optional region |

### 为什么执行阶段只拿 75%？

留出 25% 的安全余量。即使预测说"刚好能做完这批"，实际执行也可能因为
对象分配、锁竞争等随机因素而超时。75% 的缓冲区降低了超时概率。

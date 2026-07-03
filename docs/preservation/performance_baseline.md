# Performance Baseline & Non-Regression Criteria (Phase 0)

This document establishes the dynamic performance profiling methodology and defines non-regression criteria for all future engineering stages.

---

## 1. Dynamic Performance Baselines
Instead of hardcoding rigid performance limits, the platform adopts a **measured baseline strategy**:
- **Baseline Execution:** Prior to merging code changes, developers run a profiling script (`test_performance_baseline.py` or a stress-testing tool) on a standard staging database.
- **Statistical Averaging:** Measurements are collected across 100 test requests to generate average response times ($T_{avg}$) and maximum response times ($T_{max}$).
- **Database Query Baseline:** The exact count of SQL queries executed per transaction ($Q$) is recorded to prevent N+1 select bottlenecks.

## 2. Non-Regression Quality Gates
For a PR to pass the performance checks, the test execution must satisfy the following dynamic comparisons:

$$\text{Latency Criteria: } T_{new} \le 1.10 \times T_{baseline}$$

$$\text{Query Criteria: } Q_{new} \le Q_{baseline}$$

- **Latency Drift:** The new average response latency must not exceed the baseline by more than **10%**. Any drift above this limit triggers a build failure in the CI/CD pipeline.
- **Query Overhead:** The total number of executed SQL queries per request must be equal to or less than the baseline.
- **Lock Wait Drift:** DB lock times must remain within historical variance bounds.

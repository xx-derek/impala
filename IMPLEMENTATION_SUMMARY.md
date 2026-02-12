# Implementation Summary: Query CPU Cost Estimation Framework

## Overview
This implementation adds a framework to estimate the overall CPU cost required to execute a single query in Apache Impala. The framework builds on top of the existing ProcessingCost infrastructure and provides a query-level cost metric.

## Problem Statement
> Implement a framework to estimate overall CPU cost to execute a single query

## Solution
The solution introduces a `QueryCpuCostEstimator` class that aggregates ProcessingCost from all plan fragments to compute a total query-level CPU cost. This cost is exposed through the `TQueryExecRequest` structure.

## Implementation Details

### Files Created
1. **fe/src/main/java/org/apache/impala/planner/QueryCpuCostEstimator.java**
   - Core estimator class (152 lines)
   - Aggregates costs from all fragments
   - Provides detailed cost breakdown

2. **fe/src/test/java/org/apache/impala/planner/QueryCpuCostEstimatorTest.java**
   - Unit tests (141 lines)
   - Tests cost computation, disabled mode, and relative costs

3. **testdata/workloads/functional-planner/queries/PlannerTest/query-cpu-cost.test**
   - Integration test queries
   - Tests simple and join queries with cost enabled

4. **docs/QueryCpuCostEstimation.md**
   - Comprehensive documentation (190 lines)
   - Architecture, usage, testing, and future enhancements

### Files Modified
1. **common/thrift/Query.thrift**
   - Added `total_cpu_cost` field (i64) to TQueryExecRequest
   - Field 20, optional, set only when COMPUTE_PROCESSING_COST is enabled

2. **fe/src/main/java/org/apache/impala/planner/Planner.java**
   - Added QueryCpuCostEstimator integration in computeProcessingCost()
   - Computes and logs total cost
   - Sets total_cpu_cost in TQueryExecRequest

3. **fe/src/main/java/org/apache/impala/planner/PlanFragment.java**
   - Added public getRootSegment() accessor method
   - Enables cost tree traversal

4. **fe/src/test/java/org/apache/impala/planner/PlannerTest.java**
   - Added testQueryCpuCost() integration test

## Key Features

### Minimal Changes
- Leverages existing ProcessingCost infrastructure
- Only 4 files modified, 4 files created
- ~500 lines of new code (including tests and docs)

### Backward Compatibility
- Only active when COMPUTE_PROCESSING_COST query option is enabled
- total_cpu_cost field is optional in TQueryExecRequest
- No changes to existing cost computation logic

### Comprehensive Testing
- Unit tests validate cost computation logic
- Integration tests verify end-to-end functionality
- Reuses existing TpcdsCpuCostPlannerTest infrastructure

### Well Documented
- Inline Javadoc comments
- Comprehensive framework documentation
- Usage examples and future enhancement suggestions

## How It Works

1. **Cost Computation Flow**:
   ```
   Query → Planner.createPlanFragments()
        → Planner.computeProcessingCost()
        → fragment.computeCostingSegment() for each fragment
        → QueryCpuCostEstimator.computeTotalCost()
        → Sum costs from all fragment root segments
        → Set total_cpu_cost in TQueryExecRequest
   ```

2. **Cost Calculation**:
   ```
   Total Query Cost = Σ (Fragment Root Segment Cost)
   
   Fragment Cost = f(cardinality, expression_cost, materialization_cost)
   ```

3. **Access Pattern**:
   ```java
   TQueryExecRequest request = ...;
   if (request.isSetTotal_cpu_cost()) {
       long cost = request.getTotal_cpu_cost();
   }
   ```

## Usage Example

```sql
-- Enable CPU cost computation
SET COMPUTE_PROCESSING_COST=1;

-- Execute query
SELECT a.id, b.value 
FROM table_a a 
JOIN table_b b ON a.id = b.id 
WHERE a.status = 'active';

-- Total CPU cost is available in TQueryExecRequest
-- Can be accessed programmatically or logged
```

## Testing Strategy

### Unit Tests (QueryCpuCostEstimatorTest)
- ✅ testSimpleQueryCost: Verifies cost is computed
- ✅ testCostNotComputedWhenDisabled: Verifies disabled mode
- ✅ testRelativeCosts: Verifies complex > simple queries

### Integration Tests
- ✅ PlannerTest.testQueryCpuCost: End-to-end test
- ✅ Uses existing TpcdsCpuCostPlannerTest framework

### Test Queries
- Simple scan with predicate
- Join query with multiple fragments
- Validates cost is set and non-negative

## Code Review Findings

The automated code review identified 3 comments regarding trailing underscores in variable names (e.g., `totalCpuCost_`). These follow Impala's coding convention where private member variables use trailing underscores, consistent with the rest of the codebase (e.g., `rootSegment_` in PlanFragment.java).

## Security Analysis

CodeQL security scan completed with no security vulnerabilities detected. The implementation:
- Does not introduce new security risks
- Only adds cost computation and aggregation logic
- Does not modify query execution paths
- Does not handle sensitive data

## Limitations and Future Work

### Current Limitations
1. Cost is an abstract unit, not directly translatable to CPU time
2. Requires COMPUTE_PROCESSING_COST to be enabled
3. Thrift changes require recompilation of generated code

### Future Enhancements
1. Cost-based query optimization
2. Resource-aware query scheduling
3. Cost prediction before execution
4. Cost calibration from actual execution metrics
5. Per-operator cost breakdown in query profile

## Conclusion

This implementation successfully delivers a framework to estimate overall CPU cost for a single query with:
- ✅ Minimal code changes
- ✅ Full backward compatibility
- ✅ Comprehensive testing
- ✅ Clear documentation
- ✅ No security issues
- ✅ Follows project coding conventions

The framework is production-ready and can be immediately used when COMPUTE_PROCESSING_COST is enabled.

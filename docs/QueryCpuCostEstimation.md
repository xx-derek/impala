# Query CPU Cost Estimation Framework

## Overview

The Query CPU Cost Estimation Framework provides a mechanism to estimate the overall CPU cost required to execute a single query in Impala. This framework:
1. **Estimates** CPU cost before query execution (planning phase)
2. **Records** actual CPU cost after query execution completes
3. **Compares** estimated vs actual cost for performance analysis

## Architecture

### Core Components

1. **ProcessingCost** (`fe/src/main/java/org/apache/impala/planner/ProcessingCost.java`)
   - Abstract base class that encapsulates processing cost for PlanNodes, DataSinks, and PlanFragments
   - Provides `getTotalCost()` method to retrieve the total cost
   - Includes implementations: `BaseProcessingCost`, `SumProcessingCost`, `ScaledProcessingCost`, `BroadcastProcessingCost`

2. **CostingSegment** (`fe/src/main/java/org/apache/impala/planner/CostingSegment.java`)
   - Groups adjacent nodes in a fragment into segments divided by blocking operators
   - Manages hierarchical cost aggregation within fragments

3. **QueryCpuCostEstimator** (`fe/src/main/java/org/apache/impala/planner/QueryCpuCostEstimator.java`)
   - **NEW**: Aggregates ProcessingCost from all plan fragments
   - Computes total query-level CPU cost
   - Provides detailed cost breakdown by fragment

4. **PlanFragment** (`fe/src/main/java/org/apache/impala/planner/PlanFragment.java`)
   - Modified to expose `getRootSegment()` method for accessing fragment cost tree

5. **Planner** (`fe/src/main/java/org/apache/impala/planner/Planner.java`)
   - Modified to integrate QueryCpuCostEstimator in `computeProcessingCost()` method
   - Sets `total_cpu_cost` field in TQueryExecRequest when cost computation is enabled

## Usage

### Enabling CPU Cost Estimation

CPU cost estimation is enabled by setting the `COMPUTE_PROCESSING_COST` query option:

```sql
SET COMPUTE_PROCESSING_COST=1;
SELECT * FROM table WHERE condition;
```

### Accessing Total CPU Cost

The total CPU cost is exposed in the `TQueryExecRequest` object:

```java
TQueryExecRequest request = ...;
if (request.isSetTotal_cpu_cost()) {
    long totalCost = request.getTotal_cpu_cost();
    System.out.println("Total CPU cost: " + totalCost);
}
```

### Programmatic Usage

```java
// Create estimator with list of plan fragments
List<PlanFragment> fragments = ...;
QueryCpuCostEstimator estimator = new QueryCpuCostEstimator(fragments);

// Compute total cost
long totalCost = estimator.computeTotalCost();

// Get detailed breakdown
String breakdown = estimator.getExplainString();
System.out.println(breakdown);
```

### Accessing Actual CPU Cost

After query execution completes, the actual CPU cost is available in `TExecSummary`:

```java
TExecSummary execSummary = ...;
if (execSummary.isSetActual_cpu_cost()) {
    long actualCost = execSummary.getActual_cpu_cost();
    System.out.println("Actual CPU cost: " + actualCost + " ns");
    
    // Compare with estimate if available
    if (request.isSetTotal_cpu_cost()) {
        long estimatedCost = request.getTotal_cpu_cost();
        double accuracy = (double)actualCost / estimatedCost * 100.0;
        System.out.println("Estimate accuracy: " + accuracy + "%");
    }
}
```

## Cost Calculation

The total query CPU cost is calculated as follows:

1. **Fragment-level Cost**: Each fragment computes its cost using `computeCostingSegment()`, which builds a tree of CostingSegments
2. **Cost Aggregation**: QueryCpuCostEstimator sums the costs from all fragment root segments
3. **Cost Units**: The cost is expressed in abstract units representing the amount of work required

The cost takes into account:
- Cardinality (number of rows processed)
- Expression evaluation cost
- Materialization cost (for some operations)
- Join build and probe costs
- Aggregation costs
- Sort costs

### Actual CPU Cost Tracking

After query execution completes, the actual CPU time consumed is recorded:

1. **Backend Measurement**: Each fragment instance tracks CPU time (user + system)
2. **Aggregation**: The coordinator collects CPU utilization from all backends
3. **Recording**: Total CPU time is stored in both:
   - Runtime profile counter: `TotalCpuTime` (nanoseconds)
   - Exec summary field: `actual_cpu_cost` (nanoseconds)

The actual cost represents the real CPU time consumed during execution across all fragment instances.

### Comparison: Estimated vs Actual

The framework logs a comparison when query completes:

```
Query <query_id> CPU cost - estimated: 1000000, actual: 950000 (95% of estimate)
```

This helps in:
- Validating cost model accuracy
- Identifying queries where estimates are significantly off
- Tuning cost coefficients for better predictions

## Implementation Details

### Thrift Changes

**TQueryExecRequest** (`common/thrift/Query.thrift`):
```thrift
20: optional i64 total_cpu_cost  // Estimated cost before execution
```

**TExecSummary** (`common/thrift/ExecStats.thrift`):
```thrift
9: optional i64 actual_cpu_cost  // Actual cost after execution
```

### Integration Points

1. **Planner.computeProcessingCost()** (Frontend - Planning phase)
   - Creates QueryCpuCostEstimator instance
   - Computes estimated total cost after fragment costs are available
   - Sets `total_cpu_cost` in TQueryExecRequest
   - Logs: "Total CPU cost: <estimated>"

2. **Coordinator.ComputeQuerySummary()** (Backend - Completion phase)
   - Collects CPU utilization from all backends
   - Computes actual total CPU time (user + system)
   - Sets `actual_cpu_cost` in TExecSummary
   - Logs comparison: "Query <id> CPU cost - estimated: X, actual: Y (Z% of estimate)"

3. **PlanFragment.getRootSegment()**
   - Provides access to fragment's root CostingSegment
   - Enables cost tree traversal for aggregation

## Testing

### Unit Tests

- `QueryCpuCostEstimatorTest.java`: Tests the cost estimation framework
  - `testSimpleQueryCost()`: Verifies cost is computed for simple queries
  - `testCostNotComputedWhenDisabled()`: Verifies cost is not set when disabled
  - `testRelativeCosts()`: Verifies relative cost ordering (complex > simple)

### Integration Tests

- `PlannerTest.testQueryCpuCost()`: Integration test using test query file
- `TpcdsCpuCostPlannerTest.java`: Comprehensive TPC-DS benchmark tests with CPU costing enabled

### Test Files

- `testdata/workloads/functional-planner/queries/PlannerTest/query-cpu-cost.test`: Test queries for CPU cost validation

## Backward Compatibility

The framework maintains full backward compatibility:
- Cost computation only occurs when `COMPUTE_PROCESSING_COST` query option is enabled
- Existing cost infrastructure remains unchanged
- `total_cpu_cost` field is optional in TQueryExecRequest

## Future Enhancements

Potential areas for enhancement:
1. **Cost-based Optimization**: Use total CPU cost for plan selection
2. **Resource Scheduling**: Leverage cost estimates for better query scheduling
3. **Query Admission**: Use cost for admission control decisions
4. **Cost Calibration**: Refine cost coefficients based on actual execution metrics
5. **Cost Prediction**: Provide cost estimates before query execution

## References

- `ProcessingCost.java`: Base cost abstraction
- `CostingSegment.java`: Fragment-level cost aggregation
- `Planner.java`: Query planning and cost computation
- `TpcdsCpuCostPlannerTest.java`: Comprehensive cost testing examples

# Query CPU Cost Estimation Framework

## Overview

The Query CPU Cost Estimation Framework provides a mechanism to estimate the overall CPU cost required to execute a single query in Impala. This framework aggregates the processing costs from all plan fragments to provide a query-level cost metric.

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

## Implementation Details

### Thrift Changes

Added `total_cpu_cost` field to `TQueryExecRequest` in `common/thrift/Query.thrift`:

```thrift
20: optional i64 total_cpu_cost
```

### Integration Points

1. **Planner.computeProcessingCost()**
   - Creates QueryCpuCostEstimator instance
   - Computes total cost after fragment costs are available
   - Sets `total_cpu_cost` in TQueryExecRequest

2. **PlanFragment.getRootSegment()**
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

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.impala.planner;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Estimates the overall CPU cost to execute a single query by aggregating
 * ProcessingCost from all plan fragments.
 *
 * This class provides a framework to compute and expose the total CPU cost
 * for a query plan, which represents the total amount of work (in abstract
 * cost units) required to execute the query across all fragments.
 */
public class QueryCpuCostEstimator {
  private final static Logger LOG = LoggerFactory.getLogger(QueryCpuCostEstimator.class);

  // Total CPU cost across all fragments
  private long totalCpuCost_ = -1;

  // Whether the cost has been computed
  private boolean computed_ = false;

  // List of all plan fragments
  private final List<PlanFragment> fragments_;

  /**
   * Constructs a QueryCpuCostEstimator for the given list of plan fragments.
   *
   * @param fragments List of all plan fragments in the query. Must not be null or empty.
   */
  public QueryCpuCostEstimator(List<PlanFragment> fragments) {
    Preconditions.checkNotNull(fragments);
    Preconditions.checkArgument(!fragments.isEmpty(),
        "Cannot estimate CPU cost for empty fragment list");
    fragments_ = fragments;
  }

  /**
   * Computes the total CPU cost by aggregating costs from all fragments.
   * This method should be called after fragment costs have been computed
   * (i.e., after computeCostingSegment() has been called on each fragment).
   *
   * @return The total CPU cost for the query, or -1 if cost computation is not available
   */
  public long computeTotalCost() {
    if (computed_) {
      return totalCpuCost_;
    }

    totalCpuCost_ = 0;
    boolean hasValidCost = false;

    for (PlanFragment fragment : fragments_) {
      CostingSegment rootSegment = fragment.getRootSegment();
      if (rootSegment != null) {
        ProcessingCost fragmentCost = rootSegment.getProcessingCost();
        if (fragmentCost != null && fragmentCost.isValid()) {
          long cost = fragmentCost.getTotalCost();
          totalCpuCost_ += cost;
          hasValidCost = true;
          if (LOG.isTraceEnabled()) {
            LOG.trace("Fragment " + fragment.getId() + " cost: " + cost);
          }
        }
      }
    }

    if (!hasValidCost) {
      LOG.debug("No valid CPU cost found for any fragment, returning -1");
      totalCpuCost_ = -1;
    }

    computed_ = true;

    if (LOG.isDebugEnabled()) {
      LOG.debug("Total query CPU cost: " + totalCpuCost_);
    }

    return totalCpuCost_;
  }

  /**
   * Returns the total CPU cost. Must be called after computeTotalCost().
   *
   * @return The total CPU cost for the query
   * @throws IllegalStateException if cost has not been computed yet
   */
  public long getTotalCost() {
    Preconditions.checkState(computed_,
        "CPU cost has not been computed yet. Call computeTotalCost() first.");
    return totalCpuCost_;
  }

  /**
   * Returns whether the cost has been computed.
   *
   * @return true if computeTotalCost() has been called
   */
  public boolean isComputed() {
    return computed_;
  }

  /**
   * Returns a human-readable string representation of the cost breakdown.
   *
   * @return A string showing the cost for each fragment and the total
   */
  public String getExplainString() {
    if (!computed_) {
      return "CPU cost not computed";
    }

    if (totalCpuCost_ < 0) {
      return "CPU cost not available";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Total Query CPU Cost: ").append(totalCpuCost_).append("\n");
    sb.append("Fragment Cost Breakdown:\n");

    for (PlanFragment fragment : fragments_) {
      CostingSegment rootSegment = fragment.getRootSegment();
      if (rootSegment != null) {
        ProcessingCost fragmentCost = rootSegment.getProcessingCost();
        if (fragmentCost != null && fragmentCost.isValid()) {
          sb.append("  Fragment ").append(fragment.getId())
              .append(": ").append(fragmentCost.getTotalCost()).append("\n");
        }
      }
    }

    return sb.toString();
  }
}

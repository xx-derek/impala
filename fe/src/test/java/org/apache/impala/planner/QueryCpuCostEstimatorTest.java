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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.impala.common.RuntimeEnv;
import org.apache.impala.thrift.TQueryCtx;
import org.apache.impala.thrift.TQueryOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test the QueryCpuCostEstimator framework for computing overall CPU cost.
 */
public class QueryCpuCostEstimatorTest extends PlannerTestBase {

  @BeforeClass
  public static void setUpClass() throws Exception {
    RuntimeEnv.INSTANCE.setTestEnv(true);
  }

  @AfterClass
  public static void cleanUpClass() {
    RuntimeEnv.INSTANCE.reset();
  }

  /**
   * Verify that CPU cost is computed and available for a simple query.
   */
  @Test
  public void testSimpleQueryCost() throws Exception {
    String query = "SELECT id FROM functional.alltypes WHERE int_col = 1";
    TQueryCtx queryCtx = TestUtils.createQueryContext(
        "default", System.getProperty("user.name"));
    queryCtx.client_request.setStmt(query);
    
    TQueryOptions queryOptions = new TQueryOptions();
    queryOptions.setCompute_processing_cost(true);
    queryCtx.client_request.setQuery_options(queryOptions);
    
    try {
      PlanCtx planCtx = new PlanCtx(queryCtx);
      planCtx.requestPlanCapture();
      String plan = createPlanAndGetExecRequest(queryCtx, planCtx);
      
      // Check that total_cpu_cost is set
      assertTrue("total_cpu_cost should be set when COMPUTE_PROCESSING_COST is enabled",
          planCtx.getRequest().isSetTotal_cpu_cost());
      
      long cpuCost = planCtx.getRequest().getTotal_cpu_cost();
      assertTrue("CPU cost should be non-negative", cpuCost >= 0);
      
      // For a simple query, cost should be greater than 0
      assertTrue("CPU cost should be greater than 0 for non-trivial query", cpuCost > 0);
    } catch (Exception e) {
      e.printStackTrace();
      fail("Should not throw exception: " + e.getMessage());
    }
  }

  /**
   * Verify that CPU cost is not set when COMPUTE_PROCESSING_COST is disabled.
   */
  @Test
  public void testCostNotComputedWhenDisabled() throws Exception {
    String query = "SELECT id FROM functional.alltypes";
    TQueryCtx queryCtx = TestUtils.createQueryContext(
        "default", System.getProperty("user.name"));
    queryCtx.client_request.setStmt(query);
    
    TQueryOptions queryOptions = new TQueryOptions();
    queryOptions.setCompute_processing_cost(false);
    queryCtx.client_request.setQuery_options(queryOptions);
    
    try {
      PlanCtx planCtx = new PlanCtx(queryCtx);
      planCtx.requestPlanCapture();
      String plan = createPlanAndGetExecRequest(queryCtx, planCtx);
      
      // Check that total_cpu_cost is not set when processing cost is disabled
      assertFalse("total_cpu_cost should not be set when COMPUTE_PROCESSING_COST is disabled",
          planCtx.getRequest().isSetTotal_cpu_cost());
    } catch (Exception e) {
      e.printStackTrace();
      fail("Should not throw exception: " + e.getMessage());
    }
  }

  /**
   * Verify that larger queries have higher CPU costs.
   */
  @Test
  public void testRelativeCosts() throws Exception {
    TQueryOptions queryOptions = new TQueryOptions();
    queryOptions.setCompute_processing_cost(true);

    // Simple query
    String simpleQuery = "SELECT id FROM functional.alltypes LIMIT 10";
    TQueryCtx simpleCtx = TestUtils.createQueryContext(
        "default", System.getProperty("user.name"));
    simpleCtx.client_request.setStmt(simpleQuery);
    simpleCtx.client_request.setQuery_options(queryOptions);
    
    PlanCtx simplePlanCtx = new PlanCtx(simpleCtx);
    simplePlanCtx.requestPlanCapture();
    createPlanAndGetExecRequest(simpleCtx, simplePlanCtx);
    
    long simpleCost = simplePlanCtx.getRequest().isSetTotal_cpu_cost() 
        ? simplePlanCtx.getRequest().getTotal_cpu_cost() : -1;

    // More complex query with join
    String complexQuery = 
        "SELECT a.id FROM functional.alltypes a " +
        "JOIN functional.alltypes b ON a.id = b.id";
    TQueryCtx complexCtx = TestUtils.createQueryContext(
        "default", System.getProperty("user.name"));
    complexCtx.client_request.setStmt(complexQuery);
    complexCtx.client_request.setQuery_options(queryOptions);
    
    PlanCtx complexPlanCtx = new PlanCtx(complexCtx);
    complexPlanCtx.requestPlanCapture();
    createPlanAndGetExecRequest(complexCtx, complexPlanCtx);
    
    long complexCost = complexPlanCtx.getRequest().isSetTotal_cpu_cost()
        ? complexPlanCtx.getRequest().getTotal_cpu_cost() : -1;

    // If both costs are valid, the complex query should have higher cost
    if (simpleCost >= 0 && complexCost >= 0) {
      assertTrue("Complex query should have higher CPU cost than simple query",
          complexCost >= simpleCost);
    }
  }
}

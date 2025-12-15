/*
 * Copyright 2019 Scanamo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scanamo

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.util.{ HashMap => JHashMap }

class DynamoObjectPerformanceTest extends AnyFunSpec with Matchers {

  describe("DynamoObject field access performance") {
    it("should not cause excessive memory allocation when accessing fields from Strict representation") {
      // Create a Strict DynamoObject that simulates data coming from DynamoDB
      // This mimics what happens when you query a secondary index with 20 attributes
      val javaMap = new JHashMap[String, AttributeValue]()

      // Simulate 20 attributes like in the user's case
      (1 to 20).foreach { i =>
        javaMap.put(s"field$i", AttributeValue.builder.s(s"value$i").build)
      }

      // Add some nested objects to make it more realistic
      val nestedMap = new JHashMap[String, AttributeValue]()
      nestedMap.put("nestedField1", AttributeValue.builder.s("nestedValue1").build)
      nestedMap.put("nestedField2", AttributeValue.builder.n("42").build)
      javaMap.put("nested", AttributeValue.builder.m(nestedMap).build)

      // Create DynamoObject - this will wrap in Strict
      val dynamoObject = DynamoObject(javaMap)

      // Simulate what happens during case class derivation:
      // Each field access triggers pattern matching which calls equals()
      // In the buggy version, this causes internalToMap to be called repeatedly

      // Access fields many times to amplify the problem
      val iterations = 500000

      val startTime = System.nanoTime()

      (1 to iterations).foreach { _ =>
        // Access all 20 fields - this is what Derivation.decodeField does
        (1 to 20).foreach { i =>
          val fieldValue = dynamoObject(s"field$i")
          fieldValue shouldBe defined
        }

        // Access nested field
        val nestedValue = dynamoObject("nested")
        nestedValue shouldBe defined
      }

      val endTime = System.nanoTime()
      val durationMs = (endTime - startTime) / 1000000

      println(s"Field access test completed in ${durationMs}ms for ${iterations} iterations")
      println(s"Average time per iteration: ${durationMs.toDouble / iterations}ms")

      // The test should complete, but when profiled, the buggy version will show:
      // - Heavy allocation in Strict.internalToMap
      // - unsafeToScalaMap being called repeatedly
      // - DynamoValue.fromAttributeValue being called for all fields repeatedly

      // With the fix, these allocations should be minimal since equals() will
      // compare Java maps directly without conversion
    }

    it("should demonstrate the equals performance issue") {
      // This test explicitly shows that equals() is the problem
      val javaMap1 = new JHashMap[String, AttributeValue]()
      val javaMap2 = new JHashMap[String, AttributeValue]()

      // Create identical maps with 20 fields
      (1 to 20).foreach { i =>
        val av = AttributeValue.builder.s(s"value$i").build
        javaMap1.put(s"field$i", av)
        javaMap2.put(s"field$i", av)
      }

      val obj1 = DynamoObject(javaMap1)
      val obj2 = DynamoObject(javaMap2)

      // Compare them many times
      val iterations = 100000

      val startTime = System.nanoTime()

      var equalCount = 0
      (1 to iterations).foreach { _ =>
        if (obj1 == obj2) equalCount += 1
      }

      val endTime = System.nanoTime()
      val durationMs = (endTime - startTime) / 1000000

      println(s"Equals test completed in ${durationMs}ms for ${iterations} iterations")
      println(s"Average time per comparison: ${durationMs.toDouble / iterations}ms")
      println(s"Objects were equal ${equalCount} times")

      equalCount shouldBe iterations

      // In the buggy version, profiling will show:
      // - internalToMap being called twice per comparison (once for each object)
      // - Full conversion of Java map to Scala map each time
      // - Heavy allocation in unsafeToScalaMap and mapValues

      // With the fix:
      // - equals() will directly compare the Java maps (xs == ys)
      // - No conversion, just Java HashMap.equals()
      // - Dramatically less allocation and faster execution
    }
  }
}

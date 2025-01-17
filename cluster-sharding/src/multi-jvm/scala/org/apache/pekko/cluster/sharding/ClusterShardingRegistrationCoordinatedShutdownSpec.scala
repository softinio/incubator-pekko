/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor._
import pekko.cluster.MemberStatus
import pekko.testkit.{ ImplicitSender, TestProbe }

/**
 * Test for issue #28416
 */
object ClusterShardingRegistrationCoordinatedShutdownSpec extends MultiNodeClusterShardingConfig {

  val first = role("first")
  val second = role("second")
  val third = role("third")

}

class ClusterShardingRegistrationCoordinatedShutdownMultiJvmNode1
    extends ClusterShardingRegistrationCoordinatedShutdownSpec
class ClusterShardingRegistrationCoordinatedShutdownMultiJvmNode2
    extends ClusterShardingRegistrationCoordinatedShutdownSpec
class ClusterShardingRegistrationCoordinatedShutdownMultiJvmNode3
    extends ClusterShardingRegistrationCoordinatedShutdownSpec

abstract class ClusterShardingRegistrationCoordinatedShutdownSpec
    extends MultiNodeClusterShardingSpec(ClusterShardingRegistrationCoordinatedShutdownSpec)
    with ImplicitSender {

  import ClusterShardingRegistrationCoordinatedShutdownSpec._
  import MultiNodeClusterShardingSpec.ShardedEntity

  private lazy val region = ClusterSharding(system).shardRegion("Entity")

  s"Region registration during CoordinatedShutdown" must {

    "try next oldest" in within(30.seconds) {
      // second should be oldest
      join(second, second)
      join(first, second)
      join(third, second)

      awaitAssert {
        cluster.state.members.count(_.status == MemberStatus.Up) should ===(3)
      }

      val csTaskDone = TestProbe()
      runOn(third) {
        CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeClusterShutdown, "test")(() => {
          Thread.sleep(200)
          region ! 1
          expectMsg(1)
          csTaskDone.ref ! Done
          Future.successful(Done)
        })

      }

      startSharding(
        system,
        typeName = "Entity",
        entityProps = Props[ShardedEntity](),
        extractEntityId = MultiNodeClusterShardingSpec.intExtractEntityId,
        extractShardId = MultiNodeClusterShardingSpec.intExtractShardId)

      enterBarrier("before-shutdown")

      runOn(second) {
        CoordinatedShutdown(system).run(CoordinatedShutdown.UnknownReason)
        awaitCond(cluster.isTerminated)
      }

      runOn(third) {
        CoordinatedShutdown(system).run(CoordinatedShutdown.UnknownReason)
        awaitCond(cluster.isTerminated)
        csTaskDone.expectMsg(Done)
      }

      enterBarrier("after-shutdown")

      runOn(first) {
        region ! 2
        expectMsg(2)
        lastSender.path.address.hasLocalScope should ===(true)
      }

      enterBarrier("after-1")
    }
  }
}

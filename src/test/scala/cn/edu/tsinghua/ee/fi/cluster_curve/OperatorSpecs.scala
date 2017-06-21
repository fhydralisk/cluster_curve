package cn.edu.tsinghua.ee.fi.cluster_curve

import com.typesafe.config.ConfigFactory
import org.specs2.mutable.Specification
import scala.collection.JavaConversions._

/**
  * Created by hydra on 2017/6/20.
  */
class OperatorSpecs extends Specification {
  val config = ConfigFactory.parseFile(new java.io.File("/Users/hydra/Documents/coding/github/akka/akka_cluster_loss_curve/local-sample.conf")).getConfig("operator")

  println(config.getStringList("tests.custom.shell-commands.shells").toList)

  private val testList =
    for (
      testName <- config.getStringList("test").toList;
      shell <- (
        config.getStringList(s"$testName.custom.shell-commands.shells").toList
          zip
          config.getStringList(s"$testName.custom.shell-commands.names").toList
        );
      hbi <- config.getStringList(s"$testName.heartbeat-interval").toList
    ) yield ConfigFactory.parseString(
      s"""
         |test-name = $testName
         |heartbeat-interval = $hbi
         |shell = ${shell._1}
         |name = ${shell._2}
      """.stripMargin).withFallback(
      config.getConfig(testName)
        .withoutPath("heartbeat-interval")
        .withoutPath("custom")
    )
  println(testList)

  "always true" should {
    "true" in {
      1 must_== 1
    }
  }
}

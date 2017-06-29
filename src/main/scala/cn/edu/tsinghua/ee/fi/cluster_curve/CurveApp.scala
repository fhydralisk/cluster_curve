package cn.edu.tsinghua.ee.fi.cluster_curve

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

/**
  * Created by hydra on 2017/3/31.
  */
object CurveApp {
  val startupTime: Long = System.currentTimeMillis()
  def main(args: Array[String]): Unit = {
    val localConfig = ConfigFactory.parseFile(new java.io.File("local.conf"))

    val config = localConfig
      .withFallback(ConfigFactory.parseResources("application.conf"))

    val roles = config.getStringList("akka.cluster.roles")

    val system = ActorSystem("CurveMiner", config)

    if (roles contains "operator") {
      val operatorConfig = config.getConfig("operator")
      system.actorOf(Operator.props(operatorConfig), name = "operator")
    }

    if (roles contains "cooperator") {
      val cooperatorConfig = config.getConfig("cooperator")
      system.actorOf(Cooperator.props(cooperatorConfig).withDispatcher("heartbeat-dispatcher"), name = "cooperator")
    }

    if (roles contains "passive-operator") {
      val passiveOperatorConfig = config.getConfig("passive-operator")
      system.actorOf(PassiveOperator.props(passiveOperatorConfig), name = "passive-operator")
    }
  }

}


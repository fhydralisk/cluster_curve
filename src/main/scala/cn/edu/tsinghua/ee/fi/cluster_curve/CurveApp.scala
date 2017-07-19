package cn.edu.tsinghua.ee.fi.cluster_curve

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import scala.util.Try

/**
  * Created by hydra on 2017/3/31.
  */
object CurveApp {
  val startupTime: Long = System.currentTimeMillis()
  def main(args: Array[String]): Unit = {

    val configFile: String = Try(args(args.indexOf("-c") + 1)) getOrElse "local.conf"
    val localConfig = ConfigFactory.parseFile(new java.io.File(configFile))

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
      system.actorOf(PassiveOperator.props(passiveOperatorConfig))
    }
  }

}


package cn.edu.tsinghua.ee.fi.cluster_curve

import akka.actor.{PoisonPill, Props}
import cn.edu.tsinghua.ee.fi.cluster_curve.Operator.{MineComplete, StartMining}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

/**
  * Created by hydra on 2017/6/29.
  */

object PassiveOperator {
  def props(config: Config): Props = Props(new PassiveOperator(config))
}
class PassiveOperator(config: Config) extends Operator(config) {

  override val testList: List[Config] = null
  override val heartbeatTimeout: java.time.Duration = null

  import context.dispatcher

  override def preparing: Receive = {
    def pp: Receive = {
      case StartMining(mineConfig) =>
        killWorker()
        val passiveMineConfig = ConfigFactory.parseString(
          """
            |cleanup-shell = ""
            |shell = ""
          """.stripMargin).withFallback(mineConfig)
        context.system.scheduler.scheduleOnce(testInterval.toMillis millis) {
          createWorkingActor(passiveMineConfig)
        }

        context become working

      case MineComplete =>
        log.info("Complete testing, terminating...")
        killWorker()
        context.system.scheduler.scheduleOnce(5 seconds) {
          System.exit(0)
        }

      case _ =>
    }

    pp orElse super.preparing
  }

  def killWorker(): Unit = {
    log.info("Passive operator is terminating worker.")
    context.actorSelection("worker") ! PoisonPill
  }

  override def scheduleNext(): Unit = {
    context become preparing
  }

}

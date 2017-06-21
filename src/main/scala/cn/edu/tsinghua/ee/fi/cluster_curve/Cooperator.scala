/**
  * Created by hydra on 2017/6/20.
  */

package cn.edu.tsinghua.ee.fi.cluster_curve

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.{Config, ConfigFactory}


object Cooperator {
  def props(config: Config): Props = Props(new Cooperator(config))
}


class Cooperator(config: Config) extends Actor with ActorLogging {

  import Messages._

  context.system.actorOf(Service.props(ConfigFactory.parseString(
    """
      |      start-broadcast-after = -1s
      |      broadcast-interval = 30ms
      |      broadcast-quantity = 1
    """.stripMargin)), "service")

  override def receive: Receive = {
    case HeartbeatRequest =>
      sender ! HeartbeatResponse
      log.info("heartbeat...")

    case Terminate =>
      context.system.terminate()

    case _ =>

  }

}

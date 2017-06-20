/**
  * Created by hydra on 2017/6/20.
  */

package cn.edu.tsinghua.ee.fi.cluster_curve

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config


object Cooperator {
  def props(config: Config): Props = Props(new Cooperator(config))
}


class Cooperator(config: Config) extends Actor with ActorLogging {

  import Messages._

  override def receive: Receive = {
    case HeartbeatRequest =>
      sender ! HeartbeatRequest

    case Terminate =>
      context.system.terminate()

    case _ =>

  }

}

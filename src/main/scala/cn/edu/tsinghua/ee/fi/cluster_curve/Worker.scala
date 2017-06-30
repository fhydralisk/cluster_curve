package cn.edu.tsinghua.ee.fi.cluster_curve

import akka.actor.{Actor, ActorLogging, ActorSelection, Address, PoisonPill, Props}
import akka.cluster.Cluster
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import cn.edu.tsinghua.ee.fi.cluster_curve.Messages.{HeartbeatRequest, HeartbeatResponse, Terminate}
import cn.edu.tsinghua.ee.fi.cluster_curve.Operator.MineResult
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._

/**
  * Created by hydra on 2017/6/29.
  */

object Worker {
  def props(
             config: Config,
             testInterval: FiniteDuration,
             addr2selection: Address => ActorSelection
           )
           (implicit timeout: Timeout): Props = Props(new Worker(config, testInterval, addr2selection))
}


class Worker(config: Config, testInterval: FiniteDuration, addr2selection: Address => ActorSelection)
            (implicit timeout: Timeout) extends Actor with ActorLogging {

  import context.dispatcher

  private val serviceConfig = config.getConfig("service")
  private val mineAmount = config.getInt("mine-amount")
  private val hbi = config.getDuration("heartbeat-interval")
  private val shell = config.getString("shell")
  private val name = config.getString("name")
  private val testName = config.getString("test-name")
  private val cleanupShell = config.getString("cleanup-shell")

  private val cluster = Cluster(context.system)

  Thread.currentThread().setPriority(Thread.MAX_PRIORITY)

  // heartbeat statistics

  @volatile
  private var hb_receive: Map[Address, Int] = Map()

  @volatile
  private var hb_loss: Map[Address, Int] = Map()

  @volatile
  private var rtt: Map[Address, Vector[Future[Long]]] = Map()

  // driver
  private val task = context.system.scheduler.schedule(testInterval, hbi.toMillis millis) {

    def sendHeartbeat(remote: Address, timeStart: Long): Unit = {
      val futureRtt: Future[Long] = addr2selection(remote) ? HeartbeatRequest map {
        case HeartbeatResponse =>
          try {
            hb_receive = increaseInMap(hb_receive, remote)
            // rtt = increaseRttInMap(rtt, remote, System.currentTimeMillis() - timeStart)
            indicateProcess(remote)
          } catch {
            case _: NullPointerException =>
              // In case that the future is completed after the release of actor, we catch the null pointer exception here.
          }

          System.currentTimeMillis() - timeStart
        case msg @ _=>
          log.warning(s"unhandled message $msg")
          -1
      } recover {
        case _: AskTimeoutException =>
          try {
            hb_loss = increaseInMap(hb_loss, remote)
          } catch {
            case _: NullPointerException =>
          }

          -1
        case e =>
          log.warning(s"unhandled exception $e in worker")
          -1
      }
      rtt = increaseRttInMap(rtt, remote, futureRtt)
    }

    val remotes = cluster.state.members.filter { _.roles contains "cooperator" } map { _.address }
    if (remotes.isEmpty)
      log.info("Cannot find cooperator. Please check whether remote node is started.")
    else {
      if (mineAmount < 0) {
        val timeStart = System.currentTimeMillis()
        remotes foreach { remote =>
          sendHeartbeat(remote, timeStart)
        }
      } else if ((remotes diff hb_receive.keySet nonEmpty) || (hb_receive exists { _._2 < mineAmount})) {
        val timeStart = System.currentTimeMillis()
        remotes foreach { remote =>
          if (!(hb_receive contains remote) || (hb_receive(remote) < mineAmount)) {
            sendHeartbeat(remote, timeStart)
          }
        }
      } else {
        // Finished
        self ! PoisonPill
      }
    }
  }

  context.system.scheduler.scheduleOnce(testInterval) {
    context.system.actorOf(Service.props(serviceConfig), name = "service")
  }

  override def preStart(): Unit = {
    log.info(s"Starting test $testName-$name, " +
      s"heartbeat interval: ${hbi.toMillis} ms")

    log.info(s"Executing shell command $shell")
    if (shell.! != 0)
      log.warning("Shell command returns non-zero which implies a failure")

  }

  override def postStop(): Unit = {
    task.cancel()
    cleanupShell.!
    val fatherActor = context.actorSelection("../")
    Future.sequence(rtt map {
      case (address, rtts) =>
        Future.sequence(rtts) map { address -> _ }
    }) map { itRtt =>
      fatherActor ! MineResult(itRtt.toMap, testName, name)
      log.info(s"Mine for test: $testName-$name has finished.")
    }

    val remotes = cluster.state.members.filter { member =>
      (member.roles contains "cooperator") &&
      !(member.roles contains "operator") &&
      !(member.roles contains "passive-operator")
    } map { _.address }

    remotes foreach { remote =>
      addr2selection(remote) ! Terminate
    }
    context.actorSelection("/user/service") ! PoisonPill
  }

  override def receive: Receive = {
    case _ =>
  }

  def increaseInMap[T](map: Map[T, Int], key: T): Map[T, Int] =
    map + {
      if (map contains key)
        key -> (map(key) + 1)
      else
        key -> 1
    }

  def increaseRttInMap[K, V](map: Map[K, Vector[V]], key: K, element: V): Map[K, Vector[V]] =
    map + {
      if (map contains key)
        key -> (map(key) :+ element)
      else
        key -> Vector(element)
    }

  def indicateProcess(address: Address): Unit = {
    if ((hb_receive(address) * 100) / mineAmount != ((hb_receive(address) - 1) * 100) / mineAmount)
      log.info(s"Mining process:$address: ${(hb_receive(address) * 100) / mineAmount}")
  }
}
/**
  * Created by hydra on 2017/6/20.
  */

package cn.edu.tsinghua.ee.fi.cluster_curve

import java.io.FileWriter

import akka.actor.{Actor, ActorLogging, ActorPath, ActorSelection, Address, PoisonPill, Props}
import akka.cluster.Cluster
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import cn.edu.tsinghua.ee.fi.cluster_curve.Messages.{HeartbeatRequest, HeartbeatResponse, Terminate}
import cn.edu.tsinghua.ee.fi.cluster_curve.Operator.{MineComplete, MineResult, NextMine}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._

object Operator {
  def props(config: Config): Props = Props(new Operator(config))

  case class MineResult(result: Map[Address, Vector[Long]], testName: String, name: String)
  case class NextMine(config: Config)
  object MineComplete

}


class Operator(config: Config) extends Actor with ActorLogging {

  private val testInterval = config.getDuration("test-interval")
  private val heartbeatTimeout = config.getDuration("heartbeat-timeout")
  private val savePath = config.getString("save-path")

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

  private val itTest = testList.toIterator

  // statistics
  private var statistics: Vector[(String, String, Map[Address, Vector[Long]])] = Vector()

  scheduleNext()

  override def receive: Receive = {
    case _ =>

  }

  def preparing: Receive = {
    case NextMine(mineConfig) =>
      // schedule creating working actor
      context become working
      createWorkingActor(mineConfig)

    case MineComplete =>
      // Save record, terminate
      log.info("Complete testing, terminating...")
      System.exit(0)
    case _ =>
  }

  def working: Receive = {
    case MineResult(result, testName, name) =>
      // Record the result
      statistics :+= (testName, name, result)

      // Save immediately
      saveResult(testName, name, result)

      // Switch to state prepare
      scheduleNext()
    case _ =>
  }

  def scheduleNext(): Unit = {
    // schedule next miner
    context become preparing
    if (itTest.hasNext) {
      log.info(s"scheduling next mining task...")
      self ! NextMine(itTest.next())
    } else
      self ! MineComplete

  }

  def createWorkingActor(mineConfig: Config): Unit = {
    context.actorOf(Worker.props(
      mineConfig,
      testInterval.toMillis millis,
      addr2selection)(heartbeatTimeout.toMillis millis
    ).withDispatcher("heartbeat-dispatcher"))
  }

  def saveResult(testName: String, name: String, result: Map[Address, Vector[Long]]): Unit = {
    val fw = new FileWriter(savePath, true)
    try {
      fw.write(s"$testName $name ${result.size}\n")
      result foreach {
        case (address, rtts) =>
          fw.write(address.toString + "\n")
          rtts foreach { rtt =>
            fw.write(s"$rtt ")
          }
          fw.write("\n")
      }
    } catch {
      case e: Throwable =>
        log.info(s"error while writing file $savePath, exception: $e")
    } finally fw.close()
  }

  def addr2selection(address: Address): ActorSelection =
    context.actorSelection(
      ActorPath.fromString(address.protocol + "://" +
        address.hostPort +
        (self.path.parent/"cooperator").toStringWithoutAddress)
    )

  def logToFile(path: String, action: String, time: Long): Unit = {
    val fw = new FileWriter(path, true)
    try fw.write(s"Action: $action; Time: $time")
    finally fw.close()
  }
}


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
    val remotes = cluster.state.members.filter { _.roles contains "cooperator" } map { _.address }
    if (remotes.isEmpty)
      log.info("Cannot find cooperator. Please check whether remote node is started.")
    else {
      if ((remotes diff hb_receive.keySet nonEmpty) || (hb_receive exists { _._2 < mineAmount})) {
        val timeStart = System.currentTimeMillis()
        remotes foreach { remote =>
          if (!(hb_receive contains remote) || (hb_receive(remote) < mineAmount)) {
            val futureRtt = addr2selection(remote) ? HeartbeatRequest map {
              case HeartbeatResponse =>
                hb_receive = increaseInMap(hb_receive, remote)
                // rtt = increaseRttInMap(rtt, remote, System.currentTimeMillis() - timeStart)
                indicateProcess(remote)
                System.currentTimeMillis() - timeStart
              case msg @ _=>
                log.warning(s"unhandled message $msg")
                -1
            } recover {
              case _: AskTimeoutException =>
                hb_loss = increaseInMap(hb_loss, remote)
                // rtt = increaseRttInMap(rtt, remote, -1)
                -1
              case e =>
                log.warning(s"unhandled exception $e in worker")
                -1
            }
            increaseRttInMap(rtt, remote, futureRtt)
          }
        }
      } else {
        // Finished
        Future.sequence(rtt map {
          case (address, rtts) =>
            Future.sequence(rtts) map { address -> _ }
        }) map { itRtt =>
          context.actorSelection("../") ! MineResult(itRtt.toMap, testName, name)
          log.info(s"Mine for test: $testName-$name has finished.")
          self ! PoisonPill
        }

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
    val remotes = cluster.state.members.filter {
      _.roles contains "cooperator"
    } map { _.address } filterNot { _ == cluster.selfAddress }

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

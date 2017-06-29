/**
  * Created by hydra on 2017/6/20.
  */

package cn.edu.tsinghua.ee.fi.cluster_curve

import java.io.FileWriter
import java.time

import akka.actor.{Actor, ActorLogging, ActorPath, ActorSelection, Address, Props}
import akka.cluster.Cluster
import cn.edu.tsinghua.ee.fi.cluster_curve.Operator.{MineComplete, MineResult, NextMine, StartMining}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.concurrent.duration._

object Operator {
  def props(config: Config): Props = Props(new Operator(config))

  case class MineResult(result: Map[Address, Vector[Long]], testName: String, name: String)
  case class NextMine(config: Config)
  case object MineComplete
  case class StartMining(mineConfig: Config)

}


class Operator(config: Config) extends Actor with ActorLogging {

  protected val cluster = Cluster(context.system)

  protected lazy val testInterval: time.Duration = config.getDuration("test-interval")
  protected lazy val heartbeatTimeout: time.Duration = config.getDuration("heartbeat-timeout")
  protected lazy val savePath: String = config.getString("save-path")

  import context.dispatcher

  protected lazy val testList: List[Config] =
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

  private lazy val itTest = testList.toIterator

  // statistics
  private var statistics: Vector[(String, String, Map[Address, Vector[Long]])] = Vector()

  override def receive: Receive = {
    case _ =>

  }

  override def preStart(): Unit = {
    scheduleNext()
  }

  def preparing: Receive = {
    case NextMine(mineConfig) =>
      // schedule creating working actor
      context become working
      createWorkingActor(mineConfig, testInterval.toMillis millis)

      // Tell passive operators to perpare next mining
      msgToPassiveOperator(StartMining(mineConfig))


    case MineComplete =>
      // Save record, terminate
      log.info("Complete testing, terminating...")
      msgToPassiveOperator(MineComplete)
      context.system.scheduler.scheduleOnce(5 seconds) {
        System.exit(0)
      }
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

  def createWorkingActor(mineConfig: Config, ti: FiniteDuration): Unit = {
    context.actorOf(Worker.props(
      mineConfig,
      ti,
      addr2selection)(heartbeatTimeout.toMillis millis
    ).withDispatcher("heartbeat-dispatcher"), name = "worker")
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

  def msgToPassiveOperator(msg: AnyRef): Unit = {
    cluster.state.members filter { _.roles contains "passive-operator" } foreach { m =>
      addr2PassiveSelection(m.address) ! msg
    }
  }
  def addr2selection(address: Address): ActorSelection =
    context.actorSelection(
      ActorPath.fromString(address.protocol + "://" +
        address.hostPort +
        (self.path.parent/"cooperator").toStringWithoutAddress)
    )

  def addr2PassiveSelection(address: Address): ActorSelection =
    context.actorSelection(
      ActorPath.fromString(address.protocol + "://" +
        address.hostPort +
        (self.path.parent/"passive-operator").toStringWithoutAddress)
    )
}

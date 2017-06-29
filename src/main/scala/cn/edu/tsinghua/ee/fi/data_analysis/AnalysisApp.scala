/**
  * Created by hydra on 2017/6/21.
  */

package cn.edu.tsinghua.ee.fi.data_analysis

import java.io.FileWriter

import cn.edu.tsinghua.ee.fi.pingbaseddown._

import scala.collection.{AbstractIterator, breakOut}
import scala.io.Source
/**
 1. Model 1
 Dimensions:
 1) Loss rate
 2) Window size
 3) Thres
 Target curve:
 1) Missed determination
 2) Incorrect determination

 2. Model 2
 Dimensions:
 1) Loss rate
 2) Window size?
 Target curve:
 1) Proper Thres
 2) Missed
 3) Incorrect


 Method:
 + Input:
 ++ A vector of history of rtt with a loss rate, we filter packet loss.
 ++ A given window size
 ++ p = 0.4
 ++ lf = ?
 + Output:
 ++ a vector of CV(windowed)
 + Determine a preliminary <Thres> denoted by PThres:
 ++ PThres = min(CV) * 0.75
 + Calculate <Incorrect determination> denoted by ID
 ++ Let sCV0 = sort(CV_loss0)
 ++ Let Pos0 = position of PThres in sCV0
 ++ ID = Pos0/|CV_loss0| * 100%
 + Calculate <Missed determination> denoted by MD
 ++ Let sCVl = sort(CV_loss_l)
 ++ Let PosL = position of PThres in sCVl
 ++ MD = PosL = PosL/|CV_loss_l| * 100%
 +

 */

trait AnalysisConfig {
  // Noise Filter Strength
  val nfs: Int

  // Parameter of Latency Evaluator
  val p: Double
  // Windows size candidates
  val windowSizes: Vector[Int]

  // Tolerance
  val toleranceOfIncorrect: Double
  val toleranceOfMissing: Double

  // Files
  val savePath: String

  val rtts: Option[(Vector[Double], List[(String, Vector[Double])])]

  val filterPacketLoss: Boolean = true
  val lf: Double = 5.0
}

abstract class SingleFileConfig extends AnalysisConfig {
  val path: String

  override lazy val rtts: Option[(Vector[Double], List[(String, Vector[Double])])] = {
    val file = Source.fromFile(path)
    val lineIt = file.getLines()
    try {
      val it = readAGroup(lineIt)
      Some(it.next()._2, it.toList)
    } catch {
      case _: Throwable =>
        None
    } finally file.close()
  }

  private def readAGroup(reader: Iterator[String]): Iterator[(String, Vector[Double])] = {
    new AbstractIterator[(String, Vector[Double])] {
      override def hasNext: Boolean = reader.hasNext

      override def next(): (String, Vector[Double]) = (
        reader.next().trim,
        reader.drop(1).next().trim.split(" ").map { _.toDouble } (breakOut)
      )
    }
  }
}

object CFG1 extends SingleFileConfig {
  override val path: String = "/Users/hydra/Documents/curve/mine_result.log"
  override val nfs: Int = 1
  override val p: Double = 0.1
  override val windowSizes: Vector[Int] = Vector(30, 60, 90)
  override val toleranceOfIncorrect: Double = 0.0
  override val toleranceOfMissing: Double = 0.0
  override val savePath: String = "/Users/hydra/Documents/curve/results-clean"
}

object CFG2 extends SingleFileConfig {
  override val path: String = "/Users/hydra/Documents/curve/mine_result_noisy-cln.log"
  override val nfs: Int = 2
  override val p: Double = 0.1
  override val windowSizes: Vector[Int] = Vector(30, 60, 90)
  override val toleranceOfIncorrect: Double = 0.0
  override val toleranceOfMissing: Double = 0.1
  override val savePath: String = "/Users/hydra/Documents/curve/results-mininet-cln"
}

object CFG3 extends SingleFileConfig {
  override val path: String = "/Users/hydra/Documents/curve/mine_result_loss0_and_noisy2-service.log"
  override val nfs: Int = 5
  override val p: Double = 0.0
  override val windowSizes: Vector[Int] = Vector(30, 60, 90)
  override val toleranceOfIncorrect: Double = 0.0
  override val toleranceOfMissing: Double = 0.01
  override val savePath: String = "/Users/hydra/Documents/curve/results"
}

object CFG4 extends SingleFileConfig {
  override val path: String = "/Users/hydra/Documents/curve/mine_result_loss0_and_noisy2-service.log"
  override val nfs: Int = 5
  override val p: Double = 0.0
  override val windowSizes: Vector[Int] = Vector(30, 60, 90)
  override val toleranceOfIncorrect: Double = 0.0
  override val toleranceOfMissing: Double = 0.05
  override val savePath: String = "/Users/hydra/Documents/curve/results-mininet-service"

  override val filterPacketLoss: Boolean = false
  override val lf: Double = 60.0
}

object AnalysisApp {

  import CFG4._

  val evaluation: Evaluation[Double] = new AVEvaluation[Double](
    noiseFilter = new SimpleNoiseFilter[Double](nfs),
    lossImpactModel = new AdditiveLossImpactAdder[Double](lf),
    latencyEvaluator = new MinPartLatencyEvaluator[Double](p)
  )

  def main(args: Array[String]): Unit = {
    val (l0, loss) = rtts.get
    val result = if (filterPacketLoss)
      calculate(l0 filter { _ >= 0.0 }, loss map { case (name, rs) =>
        name -> rs.filter { _ >= 0.0 }
      }, windowSizes)
    else
      calculate(l0, loss, windowSizes)
    saveResult(savePath, result)
  }

  def calculate(l0History: Vector[Double], lossRtts: List[(String, Vector[Double])], windowSizes: Vector[Int]):
    Iterable[(Int, Iterable[(String, Double, Double, Double)], Iterable[(String, Vector[Double])])] = {
    windowSizes.map { dw =>
      val normalEstimator = new ThresEstimator(l0History, dw, evaluation)
      val lastTwoIt = (lossRtts map { case (name, lossRtt) =>
        val lossEstimator = new ThresEstimator(lossRtt, dw, evaluation)
        val thresCalculator = new ProperThresCalculator(normalEstimator, lossEstimator)

        val properThres = thresCalculator.properThres(toleranceOfIncorrect, toleranceOfMissing)

        //Name Thres Incorrect Miss
        (
          (name, properThres, thresCalculator.pIncorrect(properThres), thresCalculator.pMiss(properThres)),
          name -> lossEstimator.thresholds
        )
      }).unzip //FIXME: (A, (B, C)) -> (A, B, C)
      (dw, lastTwoIt._1, ("loss0" -> normalEstimator.thresholds) +: lastTwoIt._2)
    }
  }

  def saveResult(
                  path: String,
                  result: Iterable[(Int, Iterable[(String, Double, Double, Double)], Iterable[(String, Vector[Double])])]
                ): Unit = {

    result foreach { case (dw, itData, thresholds) =>
      val sPath = path + s"/window - $dw"
      val fw = new FileWriter(sPath + ".txt", false)
      val fwNames = new FileWriter(sPath + "_names.txt", false)
      val fwThresholds = new FileWriter(sPath + "_thresholds.txt", false)
      try {
        itData foreach { data =>
          fwNames.write(data._1 + "\n")
          fw.write(s"${data._2} ${data._3} ${data._4}\n")
        }
        thresholds foreach { case (name, threses) =>
          fwThresholds.write(name + "\n")
          threses foreach { thres =>
            fwThresholds.write(thres + " ")
          }
          fwThresholds.write("\n")
        }
      } catch {
        case e: Throwable =>
          println(s"error while writing file $sPath, exception: $e")
      } finally {
        fw.close()
        fwNames.close()
        fwThresholds.close()
      }
    }

  }

}

object NfAnalyzeApp {
  import CFG4._

  val thres = 1.0

  val evaluation: Evaluation[Double] = new AVEvaluation[Double](
    noiseFilter = new SimpleNoiseFilter[Double](nfs),
    lossImpactModel = new AdditiveLossImpactAdder[Double](lf),
    latencyEvaluator = new MinPartLatencyEvaluator[Double](p)
  )

  val evaluationWithoutNF: Evaluation[Double] = new AVEvaluation[Double](
    noiseFilter = new SimpleNoiseFilter[Double](0),
    lossImpactModel = new AdditiveLossImpactAdder[Double](lf),
    latencyEvaluator = new MinPartLatencyEvaluator[Double](p)
  )

  def main(args: Array[String]): Unit = {
    val estimator = new ThresEstimator(rtts.get._1, 30, evaluation)
    val estimatorWithoutNf = new ThresEstimator(rtts.head._1, 30, evaluationWithoutNF)

    val estimatorLoss = new ThresEstimator(rtts.get._2(4)._2, 30, evaluation)
    val estimatorLossWithoutNf = new ThresEstimator(rtts.get._2(4)._2, 30, evaluationWithoutNF)


    println(s"${estimator.posOf(thres)} ${estimatorWithoutNf.posOf(thres)}")
    println(s"${estimatorLoss.posOf(thres)} ${estimatorLossWithoutNf.posOf(thres)}")
  }
}

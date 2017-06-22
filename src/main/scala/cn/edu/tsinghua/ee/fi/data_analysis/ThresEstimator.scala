package cn.edu.tsinghua.ee.fi.data_analysis

import cn.edu.tsinghua.ee.fi.pingbaseddown.Evaluation

/**
  * Created by hydra on 2017/6/21.
  */
class ThresEstimator[T](rtts: Vector[T], window: Int, evaluation: Evaluation[T]) {

  val thresholds: Vector[Double] =
    rtts sliding window map { vrtt => evaluation.evaluate(vrtt.toList, 0) } toVector

  private val helper = ThresEstimateHelper(thresholds)

  val min: Double = helper.min
  val max: Double = helper.max

  def posOf(thres: Double): Double = helper.posOf(thres)
  def thresAt(pos: Double, interpolation: Boolean): Double = helper.thresAt(pos, interpolation)

}


private object ThresEstimateHelper {
  def apply(thresholds: Vector[Double]): ThresEstimateHelper = new ThresEstimateHelper(thresholds)
}


private class ThresEstimateHelper(thresholds: Vector[Double]) {

  val sortedThres: Vector[Double] = thresholds.sorted

  val max: Double = thresholds.max
  val min: Double = thresholds.min

  def posOf(thres: Double): Double =
    (if (thres <= min) {
      (sortedThres indexWhere { thres < _ } toDouble) / 2.0
    } else if (thres >= max) {
      sortedThres.size - (sortedThres.reverse indexWhere { thres > _ }) / 2.0
    } else {
      val lb = (sortedThres sliding 2) indexWhere { p => thres > p(0) && thres <= p(1) }
      val ub = (sortedThres sliding 2) indexWhere { p => thres >= p(0) && thres < p(1) }
      (lb + ub + 2) / 2.0
    }) / sortedThres.size.toDouble

  def thresAt(pos: Double, interpolation: Boolean): Double = {
    val posI = ((pos * sortedThres.size) toInt) - 1
    if (interpolation) {
      if (posI <= 0)
        min * pos * sortedThres.size
      else if (posI >= sortedThres.size - 1) {
        // TODO: How to deal with this interpolation
        max
      }
      else {
        /* linear interpolation
          f(s)+(f(t) - f(s))*p = f(s)(1-p) + f(t)p
          pg = pos * len
          p = pg - [pg]
          s = [pg] - 1 = posI
          t = s = posI + 1
          f(s)(1-p) + f(t)p =
            f(s)(1 - pg + [pg]) + f(t)(pg - [pg]) =
            f(s)(1 - pg + posI + 1) + f(t)(pg - posI - 1) =
            f(t)(pg - posI - 1) + f(s)(2 - pg + posI)
         */

        def f(x: Int) = sortedThres(x)
        val pg = pos * sortedThres.size

        f(posI + 1) * (pg - posI - 1.0) + f(posI) * (2.0 - pg + posI)
      }
    } else {
      if (posI <= 0)
        min
      else if (pos >= sortedThres.size - 1)
        max
      else
        sortedThres(posI)
    }
  }
}

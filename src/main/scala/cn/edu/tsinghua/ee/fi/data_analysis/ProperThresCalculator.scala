package cn.edu.tsinghua.ee.fi.data_analysis

/**
  * Created by hydra on 2017/6/21.
  */

class ProperThresCalculator[T](normalThresEstimator: ThresEstimator[T], lossThresEstimator: ThresEstimator[T]) {

  // TODO: middle point sometime does not make sense. for example sometimes pkt loss nodes.
  private val middlePoint = (normalThresEstimator.max + lossThresEstimator.min) / 2.0
  /**
    * Get the proper threshold value with the tolerance of missed determination.
    * @param pmd The maximum probability of miss
    * @return threshold
    */
  def properThresWithToleranceOfMissDetermine(pmd: Double): Double =
    if (pMiss(middlePoint) <= pmd)
      middlePoint
    else
      lossThresEstimator.thresAt(pmd, interpolation = true)

  /**
    * Get the proper threshold value with the tolerance of incorrect determination.
    *
    * @param pid The maximum probability of incorrectness
    * @return threshold
    */
  def properThresWithToleranceOfIncorrectDetermine(pid: Double): Double =
    if (pIncorrect(middlePoint) <= pid)
      middlePoint
    else
      normalThresEstimator.thresAt(1.0 - pid, interpolation = true)


  def properThres(pidMax: Double, pmdMax: Double): Double =
    (normalThresEstimator.thresAt(1.0 - pidMax, interpolation = true) +
      lossThresEstimator.thresAt(pmdMax, interpolation = true)) / 2.0

  /**
    * Probability of missing determination with thres.
    *
    * @param thres A given threshold
    * @return The probability
    */
  def pMiss(thres: Double): Double = lossThresEstimator.posOf(thres)

  /**
    * Probability of incorrect determination with thres.
    *
    * @param thres A given threshold
    * @return The probability
    */
  def pIncorrect(thres: Double): Double = 1.0 - normalThresEstimator.posOf(thres)
}

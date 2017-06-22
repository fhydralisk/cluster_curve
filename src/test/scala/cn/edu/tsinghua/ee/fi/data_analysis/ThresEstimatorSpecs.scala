package cn.edu.tsinghua.ee.fi.data_analysis

import org.specs2.mutable.Specification

/**
  * Created by hydra on 2017/6/21.
  */
class ThresEstimatorSpecs {

}

class ThresEstimatorHelperSpecs extends Specification {

  private val teh1 = ThresEstimateHelper(Vector(1,1,2,3,1,1,2,3))
  "teh1" should {
    "thres 0 should be at pos 0.0" in {
      teh1.posOf(0) must_== 0.0
    }
    "thres 1 should be at pos 0.25" in {
      teh1.posOf(1) must_== 0.25
    }
    "thres 2 should be at pos 0.625" in {
      teh1.posOf(2) must_== 0.625
    }
    "thres 3 should be at pos 0.875" in {
      teh1.posOf(3) must_== 0.875
    }
    "thres 4 should be at pos 1.0" in {
      teh1.posOf(4) must_== 1.0
    }

    "pos 0.0 is thres 0" in {
      teh1.thresAt(0.0, interpolation = true) must_== 0.0
    }

    "pos 0.25 is thres 1" in {
      teh1.thresAt(0.25, interpolation = true) must_== 1.0
    }
    "pos 0.625 is thres 2" in {
      teh1.thresAt(0.625, interpolation = true) must_== 2.0
    }
    "pos 0.875 is thres 3" in {
      teh1.thresAt(0.875, interpolation = true) must_== 3.0
    }
    "pos 0.8125 is thres 2.5" in {
      teh1.thresAt(0.8125, interpolation = true) must_== 2.5
    }
    "pos 1.0 is thres 3" in {
      teh1.thresAt(1.0, interpolation = true) must_== 3.0
    }
  }
}
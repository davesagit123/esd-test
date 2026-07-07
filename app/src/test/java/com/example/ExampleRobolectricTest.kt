package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.stats.EsdTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ESD Outlier Test", appName)
  }

  @Test
  fun `test tCdf and tPpf mathematical correctness`() {
    // For df = 100, t_critical for 95% single sided is ~1.66
    val cdf = EsdTest.tCdf(1.66, 100)
    assertTrue(cdf > 0.94 && cdf < 0.96)

    // Verify PPF is the inverse of CDF
    val targetP = 0.975
    val tVal = EsdTest.tPpf(targetP, 20)
    val checkP = EsdTest.tCdf(tVal, 20)
    assertEquals(targetP, checkP, 0.001)
  }

  @Test
  fun `test generalizedEsd outlier detection`() {
    // Standard dataset with two prominent outliers (85.0 and -45.0)
    val dataset = listOf(10.0, 12.0, 11.0, 13.0, 85.0, 12.0, 10.0, -45.0, 11.0, 12.0)
    val labels = dataset.mapIndexed { index, _ -> "Point $index" }

    val result = EsdTest.generalizedEsd(dataset, labels, maxOutliers = 3, alpha = 0.05)
    
    // Should detect exactly 2 outliers
    assertEquals(2, result.outlierCount)
    
    // The indices of 85.0 (idx 4) and -45.0 (idx 7) should be marked
    assertTrue(result.outlierIndices.contains(4))
    assertTrue(result.outlierIndices.contains(7))
  }

  @Test
  fun `test oneStepScores NIST grubbs-like scores`() {
    // Dataset with exactly one outlier to avoid masking on the single-step test
    val dataset = listOf(10.0, 12.0, 11.0, 13.0, 85.0, 12.0, 10.0, 11.0, 12.0)
    val scores = EsdTest.oneStepScores(dataset, alpha = 0.05)

    // Outlier point should have a score greater than 1.0 (exceeds critical limit)
    assertTrue(scores[4] > 1.0) // 85.0
    
    // Non-outlier points should have scores less than 1.0
    assertTrue(scores[0] < 1.0) // 10.0
    assertTrue(scores[3] < 1.0) // 13.0
  }
}

package com.example

import com.example.data.model.ComplaintStatus
import com.example.data.model.NoiseSeverity
import com.example.data.model.NoiseSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun noiseSeverity_classifiesDecibelLevelsAccurately() {
    assertEquals(NoiseSeverity.MODERATE, NoiseSeverity.fromDb(42))
    assertEquals(NoiseSeverity.HIGH, NoiseSeverity.fromDb(60))
    assertEquals(NoiseSeverity.CRITICAL, NoiseSeverity.fromDb(78))
    assertEquals(NoiseSourceType.LOUDSPEAKER, NoiseSourceType.fromRaw("UNKNOWN"))
    assertEquals(ComplaintStatus.SUBMITTED, ComplaintStatus.fromRaw("UNKNOWN"))
  }
}

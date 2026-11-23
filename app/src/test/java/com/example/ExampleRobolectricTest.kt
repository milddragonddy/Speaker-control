package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ComplaintStatus
import com.example.data.model.NoiseSeverity
import com.example.data.model.NoiseSourceType
import org.junit.Assert.assertEquals
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
    assertEquals("QuietCare", appName)
  }

  @Test
  fun `noise severity classifies decibel levels accurately`() {
    assertEquals(NoiseSeverity.MODERATE, NoiseSeverity.fromDb(42))
    assertEquals(NoiseSeverity.HIGH, NoiseSeverity.fromDb(60))
    assertEquals(NoiseSeverity.CRITICAL, NoiseSeverity.fromDb(78))
    assertEquals(NoiseSourceType.LOUDSPEAKER, NoiseSourceType.fromRaw("UNKNOWN"))
    assertEquals(ComplaintStatus.SUBMITTED, ComplaintStatus.fromRaw("UNKNOWN"))
  }
}

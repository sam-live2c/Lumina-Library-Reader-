package com.example

import com.example.ui.reader.calculatePageLayoutMetrics
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `calculatePageLayoutMetrics centers portrait book vertically and horizontally`() {
    // Typical phone container: 1080 x 2400.
    // Portrait book page (A4 / standard textbook 1:1.414): 1000 x 1414.
    val metrics = calculatePageLayoutMetrics(
      bmpW = 1000f,
      bmpH = 1414f,
      containerW = 1080f,
      containerH = 2400f,
      isSmartMarginFit = true
    )

    // Check that width fits container
    assertEquals(1080f, metrics.width, 0.1f)
    val expectedHeight = 1080f * (1414f / 1000f)
    assertEquals(expectedHeight, metrics.height, 0.1f)

    // Symmetrical horizontal alignment
    assertEquals(0f, metrics.left, 0.1f)

    // Symmetrical vertical alignment: equal top and bottom breathing room
    val expectedTop = (2400f - expectedHeight) / 2f
    assertEquals(expectedTop, metrics.top, 0.1f)
    val bottomMargin = 2400f - (metrics.top + metrics.height)
    assertEquals(metrics.top, bottomMargin, 0.1f)
  }

  @Test
  fun `calculatePageLayoutMetrics centers square and landscape pages`() {
    // Landscape presentation: 1920 x 1080 on 1080 x 2400
    val metrics = calculatePageLayoutMetrics(
      bmpW = 1920f,
      bmpH = 1080f,
      containerW = 1080f,
      containerH = 2400f,
      isSmartMarginFit = false
    )

    assertEquals(1080f, metrics.width, 0.1f)
    val expectedTop = (2400f - metrics.height) / 2f
    assertEquals(expectedTop, metrics.top, 0.1f)
    val bottomMargin = 2400f - (metrics.top + metrics.height)
    assertEquals(metrics.top, bottomMargin, 0.1f)
  }
}


package com.elysium369.meet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

class RelativeTimeFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return "${value.toInt()}s"
    }
}

@Composable
fun LiveGraph(
    pidLabel: String,
    unit: String,
    dataPoints: List<Pair<Long, Float>>,  // timestamp -> valor
    warningThreshold: Float,
    criticalThreshold: Float,
    secondaryPid: String? = null,         // PREMIUM: segundo PID
    secondaryData: List<Pair<Long, Float>>? = null,
    windowSeconds: Int = 60,              // configurable: 30,60,300,600
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                legend.isEnabled = secondaryPid != null
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    valueFormatter = RelativeTimeFormatter()
                    granularity = 1f
                    textColor = android.graphics.Color.WHITE
                }
                
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = android.graphics.Color.parseColor("#33FFFFFF")
                    textColor = android.graphics.Color.WHITE
                    
                    // Líneas de threshold
                    addLimitLine(LimitLine(warningThreshold, "Alerta")
                        .apply { 
                            lineColor = android.graphics.Color.YELLOW 
                            lineWidth = 1f 
                            textColor = android.graphics.Color.YELLOW
                        })
                    addLimitLine(LimitLine(criticalThreshold, "Crítico")
                        .apply { 
                            lineColor = android.graphics.Color.RED 
                            lineWidth = 1f 
                            textColor = android.graphics.Color.RED
                        })
                }
                
                axisRight.apply {
                    isEnabled = secondaryPid != null
                    textColor = android.graphics.Color.parseColor("#4FC3F7")
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { chart ->
            val entries = dataPoints.takeLast(windowSeconds * 2)
                .mapIndexed { i, (_, v) -> Entry(i.toFloat(), v) }
            
            val dataset = LineDataSet(entries, "$pidLabel ($unit)").apply {
                color = android.graphics.Color.parseColor("#FF6B35")
                setDrawCircles(false)
                lineWidth = 2f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = android.graphics.Color.parseColor("#33FF6B35")
            }
            
            val dataSets = mutableListOf<ILineDataSet>(dataset)
            
            if (isPremium && secondaryData != null && secondaryPid != null) {
                val entries2 = secondaryData.takeLast(windowSeconds * 2)
                    .mapIndexed { i, (_, v) -> Entry(i.toFloat(), v) }
                dataSets.add(LineDataSet(entries2, secondaryPid).apply {
                    color = android.graphics.Color.parseColor("#4FC3F7")
                    setDrawCircles(false)
                    lineWidth = 2f
                    axisDependency = YAxis.AxisDependency.RIGHT
                })
            }
            
            chart.data = LineData(dataSets)
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier
    )
}

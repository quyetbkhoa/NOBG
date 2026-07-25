package com.nobg.app.data

import org.json.JSONArray
import org.json.JSONObject

data class SpeedStepPoint(
    val batteryPct: Int,
    val secondsPerPct: Float,
    val cumulativeMinutes: Float
)

data class PredictionResult(
    val remainingMinutes: Int,
    val avgSecondsPerPctMap: Map<Int, Float>,
    val curvePoints: List<SpeedStepPoint>,
    val hasEnoughData: Boolean
)

object ChargingPredictor {

    fun parsePointsJson(jsonStr: String): List<ChargingPoint> {
        val list = mutableListOf<ChargingPoint>()
        if (jsonStr.isBlank()) return list
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pct = obj.getInt("pct")
                val ts = obj.getLong("timeMs")
                list.add(ChargingPoint(pct, ts))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedBy { it.batteryPct }
    }

    fun serializePointsJson(points: List<ChargingPoint>): String {
        val array = JSONArray()
        for (pt in points) {
            val obj = JSONObject()
            obj.put("pct", pt.batteryPct)
            obj.put("timeMs", pt.timestampMs)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Compute piecewise average seconds per 1% step based on all historical saved sessions.
     * Accurately predicts remaining time from currentPct up to 100%.
     */
    fun calculateNonLinearPrediction(
        currentPct: Int,
        sessions: List<ChargingSessionEntity>
    ): PredictionResult {
        val stepTimesMap = mutableMapOf<Int, MutableList<Float>>()

        // Gather interval step times across all sessions
        for (session in sessions) {
            val points = parsePointsJson(session.pointsJson)
            if (points.size < 2) continue

            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val pctDiff = p2.batteryPct - p1.batteryPct
                val timeDiffSec = (p2.timestampMs - p1.timestampMs) / 1000f

                if (pctDiff > 0 && timeDiffSec in 1f..1800f) {
                    val secPer1Pct = timeDiffSec / pctDiff
                    for (step in p1.batteryPct until p2.batteryPct) {
                        if (step in 0..99) {
                            stepTimesMap.getOrPut(step) { mutableListOf() }.add(secPer1Pct)
                        }
                    }
                }
            }
        }

        val hasEnoughData = stepTimesMap.values.sumOf { it.size } >= 5
        val avgSecMap = mutableMapOf<Int, Float>()

        // Default baseline curve fallback if historical steps are sparse
        for (p in 0..99) {
            val historicalTimes = stepTimesMap[p]
            if (historicalTimes != null && historicalTimes.isNotEmpty()) {
                avgSecMap[p] = historicalTimes.average().toFloat()
            } else {
                // Non-linear fallback profile: fast early, slow near 100%
                avgSecMap[p] = when {
                    p < 50 -> 40f     // ~40s per 1%
                    p < 80 -> 55f     // ~55s per 1%
                    p < 90 -> 90f     // ~90s per 1%
                    p < 95 -> 140f    // ~140s per 1%
                    else -> 220f      // ~220s per 1% (trickle charge)
                }
            }
        }

        // Sum remaining seconds from currentPct up to 99%
        val targetPct = 100
        val clampedCurrent = currentPct.coerceIn(0, 99)
        var totalSecNeeded = 0f
        for (p in clampedCurrent until targetPct) {
            totalSecNeeded += avgSecMap[p] ?: 60f
        }
        val remainingMinutes = (totalSecNeeded / 60f).toInt()

        // Build cumulative curve for plotting (Ox: % Pin, Oy: Accumulated time in minutes)
        val curveList = mutableListOf<SpeedStepPoint>()
        var accumSec = 0f
        for (p in 0..100) {
            val sec = avgSecMap[p] ?: 60f
            if (p > 0) accumSec += avgSecMap[p - 1] ?: 60f
            curveList.add(SpeedStepPoint(p, sec, accumSec / 60f))
        }

        return PredictionResult(
            remainingMinutes = remainingMinutes,
            avgSecondsPerPctMap = avgSecMap,
            curvePoints = curveList,
            hasEnoughData = hasEnoughData
        )
    }
}

package jp.appathy.meshihq.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * OSM の opening_hours 文法のうち、実際に日本の飲食店で頻出する範囲だけを内部JSONへ変換する。
 * 変換できなかったものは null を返し、呼び出し側が原文を opening_hours_raw に退避する。
 */
object OpeningHours {

    private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
    private val OSM_DAYS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val TIME = Regex("^\\d{1,2}:\\d{2}$")

    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.equals("24/7", true)) {
            val all = JSONObject()
            DAYS.forEach { all.put(it, JSONArray().put(JSONArray().put("00:00").put("24:00"))) }
            return all.toString()
        }
        val result = linkedMapOf<String, MutableList<Pair<String, String>>>()
        DAYS.forEach { result[it] = mutableListOf() }
        var matched = false

        for (rule in trimmed.split(";")) {
            val part = rule.trim()
            if (part.isEmpty()) continue
            val head = part.substringBefore(' ', "").trim()
            val tail = part.substringAfter(' ', "").trim()
            val days = expandDays(head) ?: return null
            if (tail.equals("off", true) || tail.equals("closed", true)) {
                days.forEach { result[it]?.clear() }
                matched = true
                continue
            }
            val spans = mutableListOf<Pair<String, String>>()
            for (span in tail.split(",")) {
                val bounds = span.trim().split("-")
                if (bounds.size != 2) return null
                val from = normalize(bounds[0]) ?: return null
                val to = normalize(bounds[1]) ?: return null
                spans.add(from to to)
            }
            days.forEach { day ->
                result[day]?.addAll(spans)
            }
            matched = true
        }
        if (!matched) return null

        val json = JSONObject()
        for (day in DAYS) {
            val array = JSONArray()
            result[day]?.forEach { span ->
                array.put(JSONArray().put(span.first).put(span.second))
            }
            json.put(day, array)
        }
        return json.toString()
    }

    fun format(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            val labels = listOf("月", "火", "水", "木", "金", "土", "日")
            val lines = mutableListOf<String>()
            DAYS.forEachIndexed { index, day ->
                val array = obj.optJSONArray(day) ?: JSONArray()
                if (array.length() == 0) {
                    lines.add(labels[index] + " 休")
                } else {
                    val spans = (0 until array.length()).joinToString(" / ") { i ->
                        val span = array.getJSONArray(i)
                        span.getString(0) + "-" + span.getString(1)
                    }
                    lines.add(labels[index] + " " + spans)
                }
            }
            lines.joinToString("\n")
        } catch (e: Exception) {
            null
        }
    }

    private fun expandDays(head: String): List<String>? {
        if (head.isBlank()) return DAYS
        val days = mutableListOf<String>()
        for (token in head.split(",")) {
            val item = token.trim()
            if (item.isBlank()) continue
            if (item.contains("-")) {
                val from = OSM_DAYS.indexOfFirst { it.equals(item.substringBefore("-").trim(), true) }
                val to = OSM_DAYS.indexOfFirst { it.equals(item.substringAfter("-").trim(), true) }
                if (from < 0 || to < 0) return null
                var i = from
                while (true) {
                    days.add(DAYS[i])
                    if (i == to) break
                    i = (i + 1) % 7
                }
            } else {
                val index = OSM_DAYS.indexOfFirst { it.equals(item, true) }
                if (index < 0) return null
                days.add(DAYS[index])
            }
        }
        return days.distinct().takeIf { it.isNotEmpty() }
    }

    private fun normalize(value: String): String? {
        val text = value.trim()
        if (!TIME.matches(text)) return null
        val hour = text.substringBefore(":").toInt()
        val minute = text.substringAfter(":").toInt()
        if (hour > 30 || minute > 59) return null
        return "%02d:%02d".format(hour, minute)
    }
}

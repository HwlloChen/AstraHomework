package com.etaris.astrahomework

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskProgressTest {
    @Test fun fractionNeedsBothCounters() {
        assertNull(TaskProgress().fraction)
        assertNull(TaskProgress(stage = "LLM generation").fraction)
        assertNull(TaskProgress(processedPages = 4).fraction)
        assertNull(TaskProgress(processedPages = 4, totalPages = 0).fraction)
    }

    @Test fun fractionTracksProcessedPages() {
        assertEquals(0f, TaskProgress(processedPages = 0, totalPages = 9).fraction!!, 0f)
        assertEquals(4f / 9f, TaskProgress(processedPages = 4, totalPages = 9).fraction!!, 0.0001f)
        assertEquals(1f, TaskProgress(processedPages = 9, totalPages = 9).fraction!!, 0f)
        // Defensive clamp: a misreporting server must never overflow the bar.
        assertEquals(1f, TaskProgress(processedPages = 42, totalPages = 9).fraction!!, 0f)
    }

    @Test fun labelPrefersPageCountsThenStage() {
        assertEquals("已分析 4 / 9 页", TaskProgress(processedPages = 4, totalPages = 9).label)
        assertEquals("正在提取 PDF 页面", TaskProgress(stage = "PDF extraction").label)
        assertEquals("正在处理", TaskProgress(stage = "internal diagnostic context").label)
        assertEquals("等待服务端处理", TaskProgress().label)
    }
}

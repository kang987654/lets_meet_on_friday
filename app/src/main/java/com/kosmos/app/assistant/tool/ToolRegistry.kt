package com.kosmos.app.assistant.tool

import javax.inject.Inject

class ToolRegistry @Inject constructor(
    addScheduleTool: AddScheduleToolExecutor,
    getScheduleTool: GetScheduleToolExecutor,
    addMemoryTool: AddMemoryToolExecutor,
    searchMemoryTool: SearchMemoryToolExecutor,
    searchWikipediaTool: SearchWikipediaToolExecutor
) {
    private val executors = mapOf(
        addScheduleTool.name to addScheduleTool,
        getScheduleTool.name to getScheduleTool,
        addMemoryTool.name to addMemoryTool,
        searchMemoryTool.name to searchMemoryTool,
        searchWikipediaTool.name to searchWikipediaTool
    )

    fun getExecutor(name: String): ToolExecutor? {
        return executors[name]
    }
}

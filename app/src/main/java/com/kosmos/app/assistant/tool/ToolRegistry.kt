package com.kosmos.app.assistant.tool

import javax.inject.Inject

class ToolRegistry @Inject constructor(
    addScheduleTool: AddScheduleToolExecutor,
    getScheduleTool: GetScheduleToolExecutor,
    searchTool: SearchToolExecutor,
    addMemoryTool: AddMemoryToolExecutor,
    searchWikipediaTool: SearchWikipediaToolExecutor
) {
    private val executors = mapOf(
        addScheduleTool.name to addScheduleTool,
        getScheduleTool.name to getScheduleTool,
        searchTool.name to searchTool,
        addMemoryTool.name to addMemoryTool,
        searchWikipediaTool.name to searchWikipediaTool
    )

    fun getExecutor(name: String): ToolExecutor? {
        return executors[name]
    }
}

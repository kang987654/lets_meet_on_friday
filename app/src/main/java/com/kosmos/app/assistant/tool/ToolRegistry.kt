package com.kosmos.app.assistant.tool

import javax.inject.Inject

class ToolRegistry @Inject constructor(
    addScheduleTool: AddScheduleToolExecutor,
    getScheduleTool: GetScheduleToolExecutor,
    searchTool: SearchToolExecutor
) {
    private val executors = mapOf(
        addScheduleTool.name to addScheduleTool,
        getScheduleTool.name to getScheduleTool,
        searchTool.name to searchTool
    )

    fun getExecutor(name: String): ToolExecutor? {
        return executors[name]
    }
}

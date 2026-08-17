package com.kangrio.byd.assistant.data

data class AssistantApp(
    val name: String = "",
    val packageName: String = "",
    val className: String = "",
    val componentName: String = "$packageName/$className"
)

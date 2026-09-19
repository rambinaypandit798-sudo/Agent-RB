package com.rbagent.assistant.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * CommandBus — Floating overlay se MainViewModel tak commands
 * bhejne ke liye app-wide singleton bus.
 */
object CommandBus {
    private val _commands = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    fun send(command: String) {
        if (command.isBlank()) return
        _commands.tryEmit(command.trim())
    }
}

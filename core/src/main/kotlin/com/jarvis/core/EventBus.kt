package com.jarvis.core

import java.util.concurrent.CopyOnWriteArraySet

fun interface EventListener {
    fun onEvent(event: Event)
}

interface EventBus {
    fun publish(event: Event)
    fun subscribe(listener: EventListener)
}

class InMemoryEventBus : EventBus {
    private val listeners = CopyOnWriteArraySet<EventListener>()

    override fun publish(event: Event) {
        listeners.forEach { it.onEvent(event) }
    }

    override fun subscribe(listener: EventListener) {
        listeners.add(listener)
    }
}

package com.jarvis.core

interface Orchestrator {
    fun dispatch(event: Event)
    fun transitionState(state: JarvisState)
    fun registerSkill(skillName: String)
}

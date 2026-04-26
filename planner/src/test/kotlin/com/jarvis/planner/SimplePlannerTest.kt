package com.jarvis.planner

import kotlin.test.Test
import kotlin.test.assertEquals

class SimplePlannerTest {
    private val planner = SimplePlanner()

    @Test
    fun createPlanBuildsSystemControlStep() {
        val plan = kotlinx.coroutines.runBlocking {
            planner.createPlan(
                intent = "SYSTEM_CONTROL",
                entities = mapOf("target" to "flashlight", "action" to "ON")
            )
        }

        assertEquals(1, plan.steps.size)
        assertEquals("SystemControl", plan.steps.first().skill)
        assertEquals("flashlight", plan.steps.first().input["target"])
        assertEquals("ON", plan.steps.first().input["action"])
    }

    @Test
    fun createPlanBuildsOpenAppStep() {
        val plan = kotlinx.coroutines.runBlocking {
            planner.createPlan(
                intent = "OPEN_APP",
                entities = mapOf("app" to "Camera")
            )
        }

        assertEquals(1, plan.steps.size)
        assertEquals("AppLauncher", plan.steps.first().skill)
        assertEquals("Camera", plan.steps.first().input["app"])
    }
}
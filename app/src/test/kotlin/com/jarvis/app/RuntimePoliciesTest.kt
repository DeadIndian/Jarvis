package com.jarvis.app

import com.jarvis.core.JarvisState
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimePoliciesTest {
    @Test
    fun runtimePolicyStartsServiceWhenEnteringActiveState() {
        val action = RuntimeServicePolicy.decide(previous = JarvisState.IDLE, current = JarvisState.ACTIVE)

        assertEquals(RuntimeServiceAction.START, action)
    }

    @Test
    fun runtimePolicyStopsServiceWhenReturningToIdle() {
        val action = RuntimeServicePolicy.decide(previous = JarvisState.HOUSE_PARTY, current = JarvisState.IDLE)

        assertEquals(RuntimeServiceAction.STOP, action)
    }

    @Test
    fun runtimePolicySkipsDuplicateStateTransitions() {
        val action = RuntimeServicePolicy.decide(previous = JarvisState.ACTIVE, current = JarvisState.ACTIVE)

        assertEquals(RuntimeServiceAction.NONE, action)
    }

    @Test
    fun voicePolicyStartsListeningWhenPermissionExists() {
        val action = VoiceInputPolicy.onVoiceButtonTapped(hasMicrophonePermission = true)

        assertEquals(VoiceInputAction.START_LISTENING, action)
    }

    @Test
    fun voicePolicyRequestsPermissionWhenMissing() {
        val action = VoiceInputPolicy.onVoiceButtonTapped(hasMicrophonePermission = false)

        assertEquals(VoiceInputAction.REQUEST_PERMISSION, action)
    }

    @Test
    fun voicePolicyMapsDeniedPermissionResult() {
        val action = VoiceInputPolicy.onPermissionResult(granted = false)

        assertEquals(VoiceInputAction.PERMISSION_DENIED, action)
    }

    @Test
    fun wakeWordPolicyStopsInHouseParty() {
        val action = WakeWordPolicy.decide(previous = JarvisState.ACTIVE, current = JarvisState.HOUSE_PARTY)

        assertEquals(WakeWordAction.STOP, action)
    }

    @Test
    fun wakeWordPolicyStopsInActive() {
        val action = WakeWordPolicy.decide(previous = JarvisState.HOUSE_PARTY, current = JarvisState.ACTIVE)

        assertEquals(WakeWordAction.STOP, action)
    }

    @Test
    fun wakeWordPolicyStopsInIdle() {
        val action = WakeWordPolicy.decide(previous = JarvisState.HOUSE_PARTY, current = JarvisState.IDLE)

        assertEquals(WakeWordAction.STOP, action)
    }

    @Test
    fun wakeWordPolicySkipsDuplicateStateTransitions() {
        val action = WakeWordPolicy.decide(previous = JarvisState.HOUSE_PARTY, current = JarvisState.HOUSE_PARTY)

        assertEquals(WakeWordAction.NONE, action)
    }
}
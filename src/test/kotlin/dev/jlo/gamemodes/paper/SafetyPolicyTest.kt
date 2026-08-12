package dev.jlo.gamemodes.paper

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafetyPolicyTest {
    @Test
    fun `active participant actions are blocked unless authorized`() {
        val policy = SafetyPolicy()
        val player = UUID.randomUUID()
        policy.activate(player)

        assertTrue(policy.blocks(player, SafetyAction.BLOCK_BREAK))
        assertTrue(policy.blocks(player, SafetyAction.TELEPORT))
        assertFalse(policy.blocks(player, SafetyAction.TELEPORT, authorized = true))
    }

    @Test
    fun `outsiders remain unaffected by participant safety policy`() {
        val policy = SafetyPolicy()
        assertFalse(policy.blocks(UUID.randomUUID(), SafetyAction.BLOCK_PLACE))
    }
}

package dev.jlo.gamemodes.paper;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyPolicyTest {
    @Test
    void activeParticipantActionsAreBlockedUnlessAuthorized() {
        SafetyPolicy policy = new SafetyPolicy();
        UUID player = UUID.randomUUID();
        policy.activate(player);

        assertTrue(policy.blocks(player, SafetyAction.BLOCK_BREAK));
        assertTrue(policy.blocks(player, SafetyAction.TELEPORT));
        assertFalse(policy.blocks(player, SafetyAction.TELEPORT, true));
    }

    @Test
    void outsidersRemainUnaffectedByParticipantSafetyPolicy() {
        SafetyPolicy policy = new SafetyPolicy();
        assertFalse(policy.blocks(UUID.randomUUID(), SafetyAction.BLOCK_PLACE));
    }
}

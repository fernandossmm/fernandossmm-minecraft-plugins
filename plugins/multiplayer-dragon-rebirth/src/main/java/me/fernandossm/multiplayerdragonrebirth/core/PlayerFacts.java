package me.fernandossm.multiplayerdragonrebirth.core;

import java.util.UUID;

/**
 * What the rules need to know about a player at one moment, read from the server by the caller.
 *
 * @param playableMode   in Survival or Adventure
 * @param vanished       hidden by a vanish plugin
 * @param exempt         has the exempt permission
 * @param hasFreeTheEnd  has the vanilla "Free the End" advancement
 */
public record PlayerFacts(UUID id, String name, boolean playableMode, boolean vanished, boolean exempt, boolean hasFreeTheEnd) {}

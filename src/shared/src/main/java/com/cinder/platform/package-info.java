/**
 * The loader-agnostic surface for Cinder.
 *
 * <p>Every entrypoint, event hook, and resource-reload registration in
 * {@code common} must go through this package, never directly through
 * Fabric-API or NeoForge APIs. This is the single chokepoint that lets a
 * later NeoForge module be added without touching feature code.
 */
package com.cinder.platform;

package com.elysium369.meet.core.plugin

import org.junit.Assert.*
import org.junit.Test

class ElysiumPluginRegistryTest {

    private fun testPlugin(
        id: String = "plugin-1",
        type: PluginType = PluginType.TOOL,
        capabilities: List<PluginCapability> = listOf(
            PluginCapability("inventory_sync", "Sync inventory from external system")
        ),
    ) = PluginRegistration(
        pluginId = PluginId(id), name = "Test Plugin $id",
        version = "1.0.0", type = type, author = "test",
        description = "Test plugin", capabilities = capabilities,
    )

    @Test
    fun `register plugin activates it`() {
        val registry = ElysiumPluginRegistry()
        assertTrue(registry.register(testPlugin()))
        assertEquals(1, registry.activePlugins.size)
    }

    @Test
    fun `cannot register duplicate plugin`() {
        val registry = ElysiumPluginRegistry()
        registry.register(testPlugin())
        assertFalse(registry.register(testPlugin()))
    }

    @Test
    fun `plugin must have at least one capability`() {
        val registry = ElysiumPluginRegistry()
        assertThrows(IllegalArgumentException::class.java) {
            registry.register(testPlugin(capabilities = emptyList()))
        }
    }

    @Test
    fun `find plugins by type`() {
        val registry = ElysiumPluginRegistry()
        registry.register(testPlugin("p1", PluginType.TOOL))
        registry.register(testPlugin("p2", PluginType.DATA_SOURCE))
        registry.register(testPlugin("p3", PluginType.TOOL))

        val tools = registry.pluginsOfType(PluginType.TOOL)
        assertEquals(2, tools.size)
    }

    @Test
    fun `find plugins by capability name`() {
        val registry = ElysiumPluginRegistry()
        registry.register(testPlugin("p1", capabilities = listOf(
            PluginCapability("gps_tracking", "GPS location tracking"),
        )))
        registry.register(testPlugin("p2", capabilities = listOf(
            PluginCapability("inventory_sync", "Inventory synchronization"),
        )))

        val gpsPlugins = registry.pluginsWithCapability("gps_tracking")
        assertEquals(1, gpsPlugins.size)
        assertEquals("p1", gpsPlugins[0].pluginId.value)
    }

    @Test
    fun `suspend plugin hides it from active list`() {
        val registry = ElysiumPluginRegistry()
        registry.register(testPlugin("p1"))
        assertEquals(1, registry.activePlugins.size)

        registry.suspend(PluginId("p1"))
        assertEquals(0, registry.activePlugins.size)
    }

    @Test
    fun `activate suspended plugin restores it`() {
        val registry = ElysiumPluginRegistry()
        registry.register(testPlugin("p1"))
        registry.suspend(PluginId("p1"))
        registry.activate(PluginId("p1"))
        assertEquals(1, registry.activePlugins.size)
    }

    @Test
    fun `all 7 plugin types exist`() {
        assertEquals(7, PluginType.entries.size)
    }

    @Test
    fun `unregister removes plugin completely`() {
        val registry = ElysiumPluginRegistry()
        registry.register(testPlugin("p1"))
        assertTrue(registry.unregister(PluginId("p1")))
        assertEquals(0, registry.registeredCount)
    }
}

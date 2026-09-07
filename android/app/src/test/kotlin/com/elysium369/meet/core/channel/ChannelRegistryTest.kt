package com.elysium369.meet.core.channel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChannelRegistryTest {

    @Test
    fun `register and find in-app provider`() {
        val registry = ChannelRegistry()
        registry.register(InAppNotificationProvider())
        assertEquals(1, registry.registeredCount)
        assertTrue(ChannelType.IN_APP in registry.availableChannels)
    }

    @Test
    fun `whatsapp provider unavailable without token`() {
        val provider = WhatsAppBusinessProvider()
        assertFalse(provider.isAvailable)
    }

    @Test
    fun `whatsapp provider available with token`() {
        val provider = WhatsAppBusinessProvider(apiToken = "test-token", phoneNumberId = "123")
        assertTrue(provider.isAvailable)
    }

    @Test
    fun `in-app provider delivers messages`() = runBlocking {
        val provider = InAppNotificationProvider()
        val msg = ChannelMessage(
            messageId = "msg-1", channelType = ChannelType.IN_APP,
            direction = MessageDirection.OUTBOUND,
            fromId = "system", toId = "user-1",
            body = "Tu mecánico está en camino",
        )
        val status = provider.sendMessage(msg)
        assertEquals(MessageStatus.DELIVERED, status)
        assertEquals(1, provider.deliveredMessages.size)
    }

    @Test
    fun `channels with feature filters correctly`() {
        val registry = ChannelRegistry()
        registry.register(InAppNotificationProvider())
        registry.register(WhatsAppBusinessProvider(apiToken = "tok", phoneNumberId = "1"))

        val withLocation = registry.channelsWithFeature(ChannelFeature.LOCATION_SHARING)
        assertTrue(ChannelType.WHATSAPP_BUSINESS in withLocation)
        assertFalse(ChannelType.IN_APP in withLocation)
    }

    @Test
    fun `whatsapp supports 6 features`() {
        val provider = WhatsAppBusinessProvider(apiToken = "tok")
        val features = ChannelFeature.entries.filter { provider.supportsFeature(it) }
        assertEquals(6, features.size)
    }

    @Test
    fun `unregister removes channel`() {
        val registry = ChannelRegistry()
        registry.register(InAppNotificationProvider())
        assertEquals(1, registry.registeredCount)
        registry.unregister(ChannelType.IN_APP)
        assertEquals(0, registry.registeredCount)
    }

    @Test
    fun `all 8 channel types exist`() {
        assertEquals(8, ChannelType.entries.size)
    }
}

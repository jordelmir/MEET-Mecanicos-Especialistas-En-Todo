package com.elysium369.meet.core.messaging

import org.junit.Assert.*
import org.junit.Test

class ElysiumMessagingEngineTest {

    private fun participant(id: String, name: String) = Participant(id, name)

    private fun engineWithConv(): Triple<ElysiumMessagingEngine, Conversation, Pair<Participant, Participant>> {
        val e = ElysiumMessagingEngine()
        val p1 = participant("alice", "Alice")
        val p2 = participant("bob", "Bob")
        val conv = e.createDirectConversation(p1, p2)
        return Triple(e, conv, p1 to p2)
    }

    // ═══════════════════════════════════════
    // CONVERSATIONS
    // ═══════════════════════════════════════

    @Test
    fun `create direct conversation`() {
        val (e, conv, _) = engineWithConv()
        assertEquals(ConversationType.DIRECT, conv.type)
        assertEquals(2, conv.participantCount)
    }

    @Test
    fun `create group conversation`() {
        val e = ElysiumMessagingEngine()
        val group = e.createGroup("Mecánicos CR", participant("admin", "Admin"),
            listOf(participant("m1", "M1"), participant("m2", "M2")))
        assertTrue(group.isGroup)
        assertEquals(3, group.participantCount)
        assertEquals("Mecánicos CR", group.title)
    }

    // ═══════════════════════════════════════
    // SENDING (WhatsApp-style)
    // ═══════════════════════════════════════

    @Test
    fun `send text message with SENT status`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Hola!")!!
        assertEquals(MessageStatus.SENT, msg.status)
        assertEquals("Hola!", msg.text)
    }

    @Test
    fun `send media message`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendMediaMessage(conv.conversationId, "alice", "Alice",
            MessageType.IMAGE, "https://photo.jpg", "image/jpeg", 50000, "Mi auto")!!
        assertTrue(msg.isMedia)
        assertEquals("Mi auto", msg.text)
    }

    @Test
    fun `send location message`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendLocationMessage(conv.conversationId, "alice", "Alice",
            9.9281, -84.0907, "San José")!!
        assertEquals(MessageType.LOCATION, msg.type)
        assertEquals(9.9281, msg.latitude!!, 0.0001)
    }

    // ═══════════════════════════════════════
    // STATUS TICKS (✓ → ✓✓ → ✓✓ blue)
    // ═══════════════════════════════════════

    @Test
    fun `mark delivered — double tick`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Test")!!
        assertTrue(e.markDelivered(conv.conversationId, msg.messageId))
        val updated = e.getConversationMessages(conv.conversationId).first()
        assertEquals(MessageStatus.DELIVERED, updated.status)
        assertNotNull(updated.deliveredAtEpochMs)
    }

    @Test
    fun `mark read — blue ticks`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Test")!!
        e.markDelivered(conv.conversationId, msg.messageId)
        assertTrue(e.markRead(conv.conversationId, msg.messageId))
        val updated = e.getConversationMessages(conv.conversationId).first()
        assertEquals(MessageStatus.READ, updated.status)
        assertNotNull(updated.readAtEpochMs)
    }

    // ═══════════════════════════════════════
    // EDITING & DELETION
    // ═══════════════════════════════════════

    @Test
    fun `edit message preserves original`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Orignal")!!
        assertTrue(e.editMessage(conv.conversationId, msg.messageId, "Original"))
        val updated = e.getConversationMessages(conv.conversationId).first()
        assertEquals("Original", updated.text)
        assertTrue(updated.isEdited)
        assertEquals("Orignal", updated.originalText)
    }

    @Test
    fun `delete for everyone hides message`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Secret")!!
        assertTrue(e.deleteForEveryone(conv.conversationId, msg.messageId))
        assertTrue(e.getConversationMessages(conv.conversationId).isEmpty())
    }

    // ═══════════════════════════════════════
    // REACTIONS & PINNING
    // ═══════════════════════════════════════

    @Test
    fun `add emoji reaction`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Listo!")!!
        assertTrue(e.addReaction(conv.conversationId, msg.messageId, "bob", "👍"))
        val updated = e.getConversationMessages(conv.conversationId).first()
        assertEquals("👍", updated.reactions["bob"])
    }

    @Test
    fun `pin message`() {
        val (e, conv, _) = engineWithConv()
        val msg = e.sendTextMessage(conv.conversationId, "alice", "Alice", "Important!")!!
        assertTrue(e.pinMessage(conv.conversationId, msg.messageId))
        assertEquals(1, e.getPinnedMessages(conv.conversationId).size)
    }

    // ═══════════════════════════════════════
    // SEARCH & FORWARD
    // ═══════════════════════════════════════

    @Test
    fun `search messages across conversations`() {
        val (e, conv, _) = engineWithConv()
        e.sendTextMessage(conv.conversationId, "alice", "Alice", "Necesito frenos nuevos")
        e.sendTextMessage(conv.conversationId, "bob", "Bob", "Los tengo disponibles")
        val results = e.searchMessages("frenos")
        assertEquals(1, results.size)
    }

    @Test
    fun `forward message with attribution`() {
        val e = ElysiumMessagingEngine()
        val conv1 = e.createDirectConversation(participant("a", "A"), participant("b", "B"))
        val conv2 = e.createDirectConversation(participant("a", "A"), participant("c", "C"))
        val orig = e.sendTextMessage(conv1.conversationId, "a", "A", "Gran noticia!")!!
        val fwd = e.forwardMessage(conv1.conversationId, orig.messageId,
            conv2.conversationId, "a", "A")!!
        assertEquals("A", fwd.forwardedFrom)
    }

    @Test
    fun `all 14 message types exist`() {
        assertEquals(14, MessageType.entries.size)
    }

    @Test
    fun `all 5 message statuses exist`() {
        assertEquals(5, MessageStatus.entries.size)
    }
}

package com.elysium369.meet.ai.data

import com.elysium369.meet.ai.domain.AiMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiConversationStore @Inject constructor() {
    private val conversations = mutableMapOf<String, MutableList<AiMessage>>()

    fun getMessages(conversationId: String): List<AiMessage> {
        return conversations[conversationId] ?: emptyList()
    }

    fun addMessage(conversationId: String, message: AiMessage) {
        val list = conversations.getOrPut(conversationId) { mutableListOf() }
        list.add(message)
    }

    fun clearConversation(conversationId: String) {
        conversations.remove(conversationId)
    }
}

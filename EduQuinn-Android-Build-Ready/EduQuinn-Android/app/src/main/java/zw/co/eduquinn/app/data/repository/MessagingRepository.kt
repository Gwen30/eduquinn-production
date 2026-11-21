package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.Conversation
import zw.co.eduquinn.app.data.model.ConversationCreateRequest
import zw.co.eduquinn.app.data.model.ConversationMessage
import zw.co.eduquinn.app.data.model.ConversationMessageRequest
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class MessagingRepository(private val api: ApiService) {

    suspend fun conversations(): Result<List<Conversation>> = safeApiCall { api.conversations().conversations }

    suspend fun startConversation(courseId: String): Result<String> = safeApiCall {
        api.createConversation(ConversationCreateRequest(courseId)).conversationId
    }

    suspend fun messages(conversationId: String): Result<List<ConversationMessage>> = safeApiCall {
        api.conversationMessages(conversationId).messages
    }

    suspend fun sendMessage(conversationId: String, body: String): Result<Unit> = safeApiCall {
        api.postConversationMessage(conversationId, ConversationMessageRequest(body))
        Unit
    }
}

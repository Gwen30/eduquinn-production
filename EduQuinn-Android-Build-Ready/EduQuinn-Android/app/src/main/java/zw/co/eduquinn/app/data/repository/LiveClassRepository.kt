package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.LiveClass
import zw.co.eduquinn.app.data.model.LiveClassJoinResponse
import zw.co.eduquinn.app.data.model.LiveClassMessage
import zw.co.eduquinn.app.data.model.LiveClassMessageRequest
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class LiveClassRepository(private val api: ApiService) {

    suspend fun list(): Result<List<LiveClass>> = safeApiCall { api.liveClasses().classes }

    suspend fun reserve(classId: String): Result<Boolean> = safeApiCall { api.reserveLiveClass(classId).reserved }

    suspend fun cancelReservation(classId: String): Result<Boolean> = safeApiCall {
        api.cancelLiveClassReservation(classId).reserved
    }

    suspend fun join(classId: String): Result<LiveClassJoinResponse> = safeApiCall { api.joinLiveClass(classId) }

    suspend fun leave(classId: String): Result<Unit> = safeApiCall { api.leaveLiveClass(classId) }

    suspend fun messages(classId: String): Result<List<LiveClassMessage>> = safeApiCall {
        api.liveClassMessages(classId).messages
    }

    suspend fun sendMessage(classId: String, message: String): Result<Unit> = safeApiCall {
        api.postLiveClassMessage(classId, LiveClassMessageRequest(message))
    }
}

package org.jetbrains.kotlinx.mcp

import org.jetbrains.kotlinx.mcp.LoggingMessageNotification.SetLevelRequest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

internal object ErrorCodeSerializer : KSerializer<ErrorCode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.jetbrains.kotlinx.mcp.ErrorCode", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: ErrorCode) {
        encoder.encodeInt(value.code)
    }

    override fun deserialize(decoder: Decoder): ErrorCode {
        val decodedString = decoder.decodeInt()
        return ErrorCode.Defined.entries.firstOrNull { it.code == decodedString }
            ?: ErrorCode.Unknown(decodedString)
    }
}

internal object RequestMethodSerializer : KSerializer<Method> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.jetbrains.kotlinx.mcp.Method", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Method) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Method {
        val decodedString = decoder.decodeString()
        return Method.Defined.entries.firstOrNull { it.value == decodedString }
            ?: Method.Custom(decodedString)
    }
}

internal object StopReasonSerializer : KSerializer<StopReason> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("org.jetbrains.kotlinx.mcp.StopReason", PrimitiveKind.STRING)

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: StopReason) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): StopReason {
        val decodedString = decoder.decodeString()
        return when (decodedString) {
            StopReason.StopSequence.value -> StopReason.StopSequence
            StopReason.MaxTokens.value -> StopReason.MaxTokens
            StopReason.EndTurn.value -> StopReason.EndTurn
            else -> StopReason.Other(decodedString)
        }
    }
}

internal object ReferencePolymorphicSerializer : JsonContentPolymorphicSerializer<Reference>(Reference::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Reference> {
        return when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ResourceReference.TYPE -> ResourceReference.serializer()
            PromptReference.TYPE -> PromptReference.serializer()
            else -> UnknownReference.serializer()
        }
    }
}

internal object PromptMessageContentPolymorphicSerializer :
    JsonContentPolymorphicSerializer<PromptMessageContent>(PromptMessageContent::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PromptMessageContent> {
        return when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ImageContent.TYPE -> ImageContent.serializer()
            TextContent.TYPE -> TextContent.serializer()
            EmbeddedResource.TYPE -> EmbeddedResource.serializer()
            else -> UnknownContent.serializer()
        }
    }
}

internal object PromptMessageContentTextOrImagePolymorphicSerializer :
    JsonContentPolymorphicSerializer<PromptMessageContentTextOrImage>(PromptMessageContentTextOrImage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<PromptMessageContentTextOrImage> {
        return when (element.jsonObject.getValue("type").jsonPrimitive.content) {
            ImageContent.TYPE -> ImageContent.serializer()
            TextContent.TYPE -> TextContent.serializer()
            else -> UnknownContent.serializer()
        }
    }
}

internal object ResourceContentsPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ResourceContents>(ResourceContents::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResourceContents> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("text") -> TextResourceContents.serializer()
            jsonObject.contains("blob") -> BlobResourceContents.serializer()
            else -> UnknownResourceContents.serializer()
        }
    }
}

internal fun selectRequestDeserializer(method: String): DeserializationStrategy<Request> {
    selectClientRequestDeserializer(method)?.let { return it }
    selectServerRequestDeserializer(method)?.let { return it }
    return CustomRequest.serializer()
}

internal fun selectClientRequestDeserializer(method: String): DeserializationStrategy<ClientRequest>? {
    return when (method) {
        Method.Defined.Ping.value -> PingRequest.serializer()
        Method.Defined.Initialize.value -> InitializeRequest.serializer()
        Method.Defined.CompletionComplete.value -> CompleteRequest.serializer()
        Method.Defined.LoggingSetLevel.value -> SetLevelRequest.serializer()
        Method.Defined.PromptsGet.value -> GetPromptRequest.serializer()
        Method.Defined.PromptsList.value -> ListPromptsRequest.serializer()
        Method.Defined.ResourcesList.value -> ListResourcesRequest.serializer()
        Method.Defined.ResourcesTemplatesList.value -> ListResourceTemplatesRequest.serializer()
        Method.Defined.ResourcesRead.value -> ReadResourceRequest.serializer()
        Method.Defined.ResourcesSubscribe.value -> SubscribeRequest.serializer()
        Method.Defined.ResourcesUnsubscribe.value -> UnsubscribeRequest.serializer()
        Method.Defined.ToolsCall.value -> CallToolRequest.serializer()
        Method.Defined.ToolsList.value -> ListToolsRequest.serializer()
        else -> null
    }
}

//internal object ClientRequestPolymorphicSerializer :
//    JsonContentPolymorphicSerializer<org.jetbrains.kotlinx.mcp.ClientRequest>(org.jetbrains.kotlinx.mcp.ClientRequest::class) {
//    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<org.jetbrains.kotlinx.mcp.ClientRequest> {
//        val method = element.jsonObject.getOrDefault("method", null)?.jsonPrimitive?.content
//            ?: error("No method in $element")
//
//        return org.jetbrains.kotlinx.mcp.selectClientRequestDeserializer(method)
//            ?: org.jetbrains.kotlinx.mcp.UnknownMethodRequestOrNotification.serializer()
//    }
//}

private fun selectClientNotificationDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification>? {
    return when (element.jsonObject.getValue("method").jsonPrimitive.content) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsInitialized.value -> InitializedNotification.serializer()
        Method.Defined.NotificationsRootsListChanged.value -> RootsListChangedNotification.serializer()
        else -> null
    }
}

internal object ClientNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientNotification>(ClientNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientNotification> {
        return selectClientNotificationDeserializer(element)
            ?: UnknownMethodRequestOrNotification.serializer()
    }
}

internal fun selectServerRequestDeserializer(method: String): DeserializationStrategy<ServerRequest>? {
    return when (method) {
        Method.Defined.Ping.value -> PingRequest.serializer()
        Method.Defined.SamplingCreateMessage.value -> CreateMessageRequest.serializer()
        Method.Defined.RootsList.value -> ListRootsRequest.serializer()
        else -> null
    }
}

//internal object ServerRequestPolymorphicSerializer :
//    JsonContentPolymorphicSerializer<org.jetbrains.kotlinx.mcp.ServerRequest>(org.jetbrains.kotlinx.mcp.ServerRequest::class) {
//    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<org.jetbrains.kotlinx.mcp.ServerRequest> {
//        return org.jetbrains.kotlinx.mcp.selectServerRequestDeserializer(element)
//            ?: org.jetbrains.kotlinx.mcp.UnknownMethodRequestOrNotification.serializer()
//    }
//}

internal fun selectServerNotificationDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification>? {
    return when (element.jsonObject.getValue("method").jsonPrimitive.content) {
        Method.Defined.NotificationsCancelled.value -> CancelledNotification.serializer()
        Method.Defined.NotificationsProgress.value -> ProgressNotification.serializer()
        Method.Defined.NotificationsMessage.value -> LoggingMessageNotification.serializer()
        Method.Defined.NotificationsResourcesUpdated.value -> ResourceUpdatedNotification.serializer()
        Method.Defined.NotificationsResourcesListChanged.value -> ResourceListChangedNotification.serializer()
        Method.Defined.ToolsList.value -> ToolListChangedNotification.serializer()
        Method.Defined.PromptsList.value -> PromptListChangedNotification.serializer()
        else -> null
    }
}

internal object ServerNotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerNotification>(ServerNotification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerNotification> {
        return selectServerNotificationDeserializer(element)
            ?: UnknownMethodRequestOrNotification.serializer()
    }
}

internal object NotificationPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Notification>(Notification::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Notification> {
        return selectClientNotificationDeserializer(element)
            ?: selectServerNotificationDeserializer(element)
            ?: UnknownMethodRequestOrNotification.serializer()
    }
}

internal object RequestPolymorphicSerializer :
    JsonContentPolymorphicSerializer<Request>(Request::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Request> {
        val method = element.jsonObject.getOrDefault("method", null)?.jsonPrimitive?.content ?: run {
            System.err.println("No method in $element")
            Throwable().printStackTrace(System.err)
            error("No method in $element")
        }

        return selectClientRequestDeserializer(method)
            ?: selectServerRequestDeserializer(method)
            ?: UnknownMethodRequestOrNotification.serializer()
    }
}

/**
 * Server messages schemas
 *
 * We deserialize by unique keys
 *
 * ```
 * org.jetbrains.kotlinx.mcp.CallToolResult {
 *     content: org.jetbrains.kotlinx.mcp.PromptMessageContent {
 *         type: String,
 *     }
 *     isError: Boolean?,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.CompatibilityCallToolResult {
 *     content: org.jetbrains.kotlinx.mcp.PromptMessageContent {
 *         type: String,
 *     },
 *     isError: Boolean?,
 *     _meta: JsonObject,
 *     toolResult: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.CompleteResult {
 *     completion: Completion {
 *         values: Array<String>,
 *         total: Int?,
 *         hasMore: Boolean?,
 *     }
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.GetPromptResult {
 *     description: String?,
 *     messages: Array<org.jetbrains.kotlinx.mcp.PromptMessage>,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.InitializeResult {
 *     protocolVersion: String,
 *     capabilities: org.jetbrains.kotlinx.mcp.ServerCapabilities,
 *     serverInfo: org.jetbrains.kotlinx.mcp.Implementation,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.ListPromptsResult {
 *     prompts: Array<org.jetbrains.kotlinx.mcp.Prompt>,
 *     nextCursor: org.jetbrains.kotlinx.mcp.Cursor?,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.ListResourceTemplatesResult {
 *     resourceTemplates: Array<org.jetbrains.kotlinx.mcp.ResourceTemplate>,
 *     nextCursor: org.jetbrains.kotlinx.mcp.Cursor?,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.ListResourcesResult {
 *     resources: Array<org.jetbrains.kotlinx.mcp.Resource>,
 *     nextCursor: org.jetbrains.kotlinx.mcp.Cursor?,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.ListToolsResult {
 *     tools: Array<org.jetbrains.kotlinx.mcp.Tool>,
 *     nextCursor: org.jetbrains.kotlinx.mcp.Cursor?,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.ReadResourceResult {
 *     contents: Array<org.jetbrains.kotlinx.mcp.ResourceContents>,
 *     _meta: JsonObject,
 * }
 * ```
 */
private fun selectServerResultDeserializer(element: JsonElement): DeserializationStrategy<ServerResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("tools") -> ListToolsResult.serializer()
        jsonObject.contains("resources") -> ListResourcesResult.serializer()
        jsonObject.contains("resourceTemplates") -> ListResourceTemplatesResult.serializer()
        jsonObject.contains("prompts") -> ListPromptsResult.serializer()
        jsonObject.contains("capabilities") -> InitializeResult.serializer()
        jsonObject.contains("description") -> GetPromptResult.serializer()
        jsonObject.contains("completion") -> CompleteResult.serializer()
        jsonObject.contains("toolResult") -> CompatibilityCallToolResult.serializer()
        jsonObject.contains("contents") -> ReadResourceResult.serializer()
        jsonObject.contains("content") -> CallToolResult.serializer()
        else -> null
    }
}

/**
 * Client messages schemas
 *
 * We deserialize by unique keys
 *
 * ```
 * org.jetbrains.kotlinx.mcp.CreateMessageResult {
 *     model: String,
 *     stopReason: org.jetbrains.kotlinx.mcp.StopReason?,
 *     role: org.jetbrains.kotlinx.mcp.Role,
 *     content: org.jetbrains.kotlinx.mcp.PromptMessageContentTextOrImage,
 *     _meta: JsonObject,
 * }
 *
 * org.jetbrains.kotlinx.mcp.ListRootsResult {
 *     roots: Array<org.jetbrains.kotlinx.mcp.Root>,
 *     _meta: JsonObject,
 * }
 *```
 */
private fun selectClientResultDeserializer(element: JsonElement): DeserializationStrategy<ClientResult>? {
    val jsonObject = element.jsonObject
    return when {
        jsonObject.contains("model") -> CreateMessageResult.serializer()
        jsonObject.contains("roots") -> ListRootsResult.serializer()
        else -> null
    }
}

internal object ServerResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ServerResult>(ServerResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServerResult> {
        return selectServerResultDeserializer(element)
            ?: EmptyRequestResult.serializer()
    }
}

internal object ClientResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<ClientResult>(ClientResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ClientResult> {
        return selectClientResultDeserializer(element)
            ?: EmptyRequestResult.serializer()
    }
}

internal object RequestResultPolymorphicSerializer :
    JsonContentPolymorphicSerializer<RequestResult>(RequestResult::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<RequestResult> {
        return selectClientResultDeserializer(element)
            ?: selectServerResultDeserializer(element)
            ?: EmptyRequestResult.serializer()
    }
}

internal object JSONRPCMessagePolymorphicSerializer :
    JsonContentPolymorphicSerializer<JSONRPCMessage>(JSONRPCMessage::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JSONRPCMessage> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.contains("message") -> JSONRPCError.serializer()
            !jsonObject.contains("method") -> JSONRPCResponse.serializer()
            jsonObject.contains("id") -> JSONRPCRequest.serializer()
            else -> JSONRPCNotification.serializer()
        }
    }
}

internal val EmptyJsonObject = JsonObject(emptyMap())

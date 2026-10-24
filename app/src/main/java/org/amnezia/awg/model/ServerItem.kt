package org.amnezia.awg.model

enum class ServerCategory {
    REGULAR,
    SPECIAL
}

enum class ServerType {
    REGULAR,
    AI,
    COUNTRY
}

data class ServerItem(
    val id: String,
    val assetFileName: String,
    val displayName: String,
    val category: ServerCategory,
    val serverType: ServerType,
    val subtitle: String? = null,
    val description: String,
    val isCountry: Boolean = false,
    val flagEmoji: String? = null
)

object ServerRepository {
    val SERVERS = listOf(
        ServerItem(
            id = "server_8",
            assetFileName = "server_8.conf",
            displayName = "Сервер #8",
            category = ServerCategory.REGULAR,
            serverType = ServerType.REGULAR,
            description = "Работает Telegram, YouTube (Без рекламы), Discord и другие замедленные сервисы. Скорость остаётся обычной, страна не меняется."
        ),
        ServerItem(
            id = "server_ai_2",
            assetFileName = "server_ai_2.conf",
            displayName = "Сервер AI 2",
            category = ServerCategory.SPECIAL,
            serverType = ServerType.AI,
            description = "Этот сервер специально настроен для бесперебойной работы с популярными сервисами искусственного интеллекта. Работают без ограничений: ChatGPT (OpenAI), Claude (Anthropic), Google Gemini."
        ),
        ServerItem(
            id = "server_germany",
            assetFileName = "server_germany.conf",
            displayName = "🇩🇪 Германия",
            category = ServerCategory.SPECIAL,
            serverType = ServerType.COUNTRY,
            subtitle = "Ограниченная низкая скорость",
            description = "Сервер 🇩🇪 Германия находится в списке специальных серверов. Скорость может быть снижена при высокой нагрузке сети.",
            isCountry = true,
            flagEmoji = "🇩🇪"
        )
    )

    fun findById(id: String?): ServerItem? = SERVERS.find { it.id == id }

    fun getDisplayName(id: String?): String = findById(id)?.displayName ?: (id ?: "Сервер #8")
}

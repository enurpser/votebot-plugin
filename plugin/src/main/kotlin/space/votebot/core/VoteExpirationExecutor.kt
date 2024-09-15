package space.votebot.core

import com.kotlindiscord.kord.extensions.utils.dm
import dev.kord.common.annotation.KordExperimental
import dev.kord.common.annotation.KordUnsafe
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.schlaubi.mikbot.plugin.api.pluginSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock
import org.litote.kmongo.div
import org.litote.kmongo.eq
import org.litote.kmongo.not
import space.votebot.common.models.FinalPollSettings
import space.votebot.common.models.Poll

internal val ExpirationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val expirationCache = mutableMapOf<String, Job>()

suspend fun rescheduleAllPollExpires(kord: Kord) = coroutineScope {
    VoteBotDatabase.polls.find(not(Poll::settings / FinalPollSettings::deleteAfter eq null))
        .toFlow()
        .onEach { poll ->
            poll.addExpirationListener(kord)
        }.launchIn(this)
}

@OptIn(KordUnsafe::class, KordExperimental::class)
fun Poll.addExpirationListener(kord: Kord) {
    val duration = settings.deleteAfter ?: error("This vote does not have an expiration Date")
    val expireAt = createdAt + duration

    expirationCache[id]?.cancel()
    expirationCache[id] = ExpirationScope.launch {
        val timeUntilExpiry = expireAt - Clock.System.now()
        if (!timeUntilExpiry.isNegative()) {
            delay(timeUntilExpiry)
        }

        close(kord, {
            kord.getUser(Snowflake(authorId))!!.dm {
                it()
            }!!
        }, pluginSystem::translate, guild = kord.unsafe.guild(Snowflake(guildId)))
    }
}

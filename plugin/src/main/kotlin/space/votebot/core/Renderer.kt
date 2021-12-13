package space.votebot.core

import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.behavior.edit
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.kord.rest.request.KtorRequestException
import dev.schlaubi.mikbot.plugin.api.util.effectiveAvatar
import dev.schlaubi.mikbot.plugin.api.util.embed
import dev.schlaubi.mikbot.plugin.api.util.forEachParallel
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.datetime.Clock
import mu.KotlinLogging
import space.votebot.common.models.Poll
import space.votebot.common.models.sumUp
import space.votebot.pie_char_service.client.PieChartCreateRequest
import space.votebot.pie_char_service.client.PieChartServiceClient
import space.votebot.pie_char_service.client.Vote
import space.votebot.util.toBehavior
import java.text.DecimalFormat

private val percentage = DecimalFormat("#.##%")

const val block = "■"
const val blockBarLength = 30

private val pieChartService = PieChartServiceClient(VoteBotConfig.PIE_CHART_SERVICE_URL)

private val LOG = KotlinLogging.logger { }

suspend fun Poll.updateMessages(kord: Kord, removeButton: Boolean = false, highlightWinner: Boolean = false) {
    val pieChart = if (highlightWinner && settings.showChartAfterClose) {
        pieChartService
            .createPieChart(toPieChartCreateRequest())
            .toInputStream()
    } else {
        null
    }

    messages.forEachParallel { message ->
        try {
            message.toBehavior(kord).edit {
                if (pieChart != null) {
                    addFile("chart.png", pieChart)
                } else {
                    content = ""
                    embeds = mutableListOf(toEmbed(kord, highlightWinner))
                    if (removeButton) {
                        components = mutableListOf()
                    } else {
                        addButtons(this@updateMessages)
                    }
                }
            }
        } catch (ignored: KtorRequestException) {
            LOG.debug(ignored) { "An error occurred whilst updating a poll message" }
        }
    }
}

fun MessageModifyBuilder.addButtons(poll: Poll) {
    poll.options.withIndex().sortedBy { (_, option) -> option.position }.chunked(5).forEach { options ->
            actionRow {
                options.forEach { (index, option) ->
                    interactionButton(ButtonStyle.Primary, "vote_$index") {
                        label = option.option
                    }
                }
            }
        }
}

suspend fun Poll.toEmbed(kord: Kord, highlightWinner: Boolean): EmbedBuilder = embed {
    title = this@toEmbed.title

    author {
        val user = kord.getUser(Snowflake(authorId))
        name = user?.username
        icon = user?.effectiveAvatar
    }

    val names = options
        .sortedBy { it.position }
        .joinToString("\n") { "${it.position + 1}. ${it.option}" }

    val totalVotes = votes.sumOf { it.amount }
    val results = sumUp().joinToString(separator = "\n") { (option, _, votePercentage) ->
            val blocksForOption = (votePercentage * blockBarLength).toInt()

            " ${option.position + 1} | ${
                block.repeat(blocksForOption).padEnd(blockBarLength)
            } | (${percentage.format(votePercentage)})"
        }

    description = """
        $names
        
        ```$results```
    """.trimIndent()


    if (settings.deleteAfter != null) {
        val deleteAt = createdAt + settings.deleteAfter!!
        if (deleteAt > Clock.System.now()) {
            field {
                name = "Will end in"
                value = deleteAt.toDiscord(TimestampType.RelativeTime)
            }
        }
    }

    if (highlightWinner) {
        val options = sumUp().groupBy { it.amount }
        val maxVotes = options.keys.maxOrNull()!!

        val winners = options[maxVotes]!!

        field {
            name = if (winners.size > 1) "Winners" else "Winner"
            value = if (winners.isEmpty()) "No one voted" else winners.joinToString(", ") { it.option.option }
        }
    }

    field {
        name = "Total Votes"
        value = totalVotes.toString()
    }

    timestamp = createdAt
}

private fun Poll.toPieChartCreateRequest(): PieChartCreateRequest {
    val votes = sumUp()

    return PieChartCreateRequest(
        title,
        512, 512,
        votes.map { (option, count) -> Vote(count, option.option) }
    )
}

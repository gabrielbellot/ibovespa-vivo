package net.mrgaabriel.ibovespavivo

import com.github.kevinsawicki.http.HttpRequest
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.request.SendMessage
import mu.KotlinLogging
import net.mrgaabriel.ibovespavivo.config.Config
import net.mrgaabriel.ibovespavivo.utils.url
import twitter4j.Twitter
import twitter4j.TwitterFactory
import twitter4j.conf.ConfigurationBuilder
import java.io.File
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue


object IbovespaVivo {

    val logger = KotlinLogging.logger {}

    lateinit var config: Config

    var lastRate: Double
        get() = File("last_rate.txt").readText().toDouble()
        set(value) = File("last_rate.txt").writeText(value.toString())

    @JvmStatic
    fun main(args: Array<String>) {
        val file = File("config.json")

        if (!file.exists()) {
            logger.warn { "Parece que é a primeira vez que você iniciou o bot! Configure-o no arquivo \"config.json\"" }

            file.createNewFile()
            file.writeText(Gson().toJson(Config()))

            return
        }

        // MOMENTO GAMBIARRA
        val lastRateFile = File("last_rate.txt")
        if (!lastRateFile.exists()) {
            lastRateFile.createNewFile()
            lastRateFile.writeText("0")
        }

        config = Gson().fromJson(file.readText(), Config::class.java)

        val twitter = if (config.twitter.enabled)
            setupTwitter()
        else
            null

        val telegram = if (config.telegram.enabled)
            setupTelegram()
        else
            null

        while (true) {
            val quote = getQuote()
            val today = LocalDate.now()

            if (quote.latestTradingDay != today) {
                Thread.sleep(3 * 60 * 1000)
                continue
            }
            
            logger.info { "Rate: ${quote.price}" }
            logger.info { "É diferente de $lastRate? ${lastRate != quote.price}" }

            if (lastRate == quote.price) {
                Thread.sleep(3 * 60 * 1000)
                continue
            }

            logger.info { "A diferença é maior que 2 pontos? ${(quote.price - lastRate).absoluteValue > 2}" }

            if ((quote.price - lastRate).absoluteValue > 2) {
                Thread.sleep(3 * 60 * 1000)
                continue
            }

            logger.info { "Atualizando status!" }

            val now = OffsetDateTime.now()
            val timestamp = now.format(DateTimeFormatter.ofPattern("HH:mm"))

            val msg = "${if (quote.price > quote.open) "\uD83D\uDCC8" else "\uD83D\uDCC9"} ${quote.price} pontos (${quote.changePercent}%) - ás $timestamp"

            twitter?.updateStatus(msg)?.also {
                logger.info { "Postado no Twitter! ${it.url()}" }
            }

            telegram?.execute(SendMessage("@${config.telegram.channel}", msg))?.also {
                logger.info { "Enviado no Telegram!" }
            }

            lastRate = quote.price

            Thread.sleep(3 * 60 * 1000)
        }
    }

    fun setupTwitter(): Twitter? {
        return try {
            val twitterConfig = ConfigurationBuilder()

            twitterConfig.setOAuthConsumerKey(config.twitter.consumerKey)
                .setOAuthConsumerSecret(config.twitter.consumerSecret)
                .setOAuthAccessToken(config.twitter.accessToken)
                .setOAuthAccessTokenSecret(config.twitter.accessSecret)

            val factory = TwitterFactory(twitterConfig.build())
            factory.instance
        } catch (e: Exception) {
            null
        }
    }

    fun setupTelegram(): TelegramBot? {
        return try {
            TelegramBot(config.telegram.token)
        } catch (e: Exception) {
            null
        }
    }

    fun getQuote(): Quote {
        val request = HttpRequest.get("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=^BVSP&apikey=${config.apiKey}")
            .acceptJson()

        // boom headshot
        if (!request.ok()) {
            throw RuntimeException("Request is not OK!")
        }

        val payload = JsonParser().parse(request.body()).asJsonObject

        return Quote(payload)
    }
}

// Com base no endpoint QUOTE da API alphavantage.co
class Quote(
    payload: JsonObject
) {

    val globalQuote = payload["Global Quote"].asJsonObject

    val symbol = globalQuote["01. symbol"].asString
    val open = limitDecimal(globalQuote["02. open"].asDouble)
    val high = limitDecimal(globalQuote["03. high"].asDouble)
    val low = limitDecimal(globalQuote["04. low"].asDouble)
    val price = limitDecimal(globalQuote["05. price"].asDouble)
    val volume = globalQuote["06. volume"].asBigInteger
    val latestTradingDay = try { LocalDate.parse(globalQuote["07. latest trading day"].asString) } catch (e: Exception) {/* whatever */}
    val previousClose = limitDecimal(globalQuote["08. previous close"].asDouble)
    val change = limitDecimal(globalQuote["09. change"].asDouble)

    // vem com um símbolo de %, então considerar como string, remover o símbolo e converter para double
    val changePercent = limitDecimal(globalQuote["10. change percent"].asString.replace("%", "").toDouble())

    private fun limitDecimal(double: Double): Double {
        return DecimalFormat("0.00").format(double).replace(",", ".").toDouble()
    }

}
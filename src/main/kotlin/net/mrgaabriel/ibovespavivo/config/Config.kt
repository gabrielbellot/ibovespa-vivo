package net.mrgaabriel.ibovespavivo.config

class Config(val twitter: TwitterConfig = TwitterConfig(),
             val telegram: TelegramConfig = TelegramConfig(),
             val apiKey: String = "API Key (alphavantage.co)"
)

class TwitterConfig(
    val enabled: Boolean = true,
    val consumerKey: String = "Twitter Consumer API key",
    val consumerSecret: String = "Twitter Consumer API Secret key",
    val accessToken: String = "Twitter Access Token",
    val accessSecret: String = "Twitter Secret Access Token"
)

class TelegramConfig(
    val enabled: Boolean = true,
    val token: String = "Telegram Bot Token",
    val channel: String = "Telegram Channel"
)
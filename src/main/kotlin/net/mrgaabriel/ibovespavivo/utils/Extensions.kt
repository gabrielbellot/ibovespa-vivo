package net.mrgaabriel.ibovespavivo.utils

import twitter4j.Status

fun Status.url() = "https://twitter.com/${this.user.screenName}/status/${this.id}"
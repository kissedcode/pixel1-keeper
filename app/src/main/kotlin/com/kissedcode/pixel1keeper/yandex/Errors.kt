package com.kissedcode.pixel1keeper.yandex

class SessionExpired(msg: String = "session expired") : Exception(msg)
class RateLimited(msg: String = "rate limited") : Exception(msg)
class YandexApiError(msg: String) : Exception(msg)

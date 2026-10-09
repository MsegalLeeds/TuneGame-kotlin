package com.msegal.tunegame.shared.api

fun createTuneGameApi(
    baseUrl: String
): TuneGameApi =
    KtorTuneGameApi(
        baseUrl = baseUrl,
        client = createHttpClient()
    )
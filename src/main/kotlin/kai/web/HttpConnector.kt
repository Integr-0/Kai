package net.integr.kai.web

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object HttpConnector {
    val httpClient: HttpClient = HttpClient.newHttpClient()

    fun newJsonRequestWithBearer(url: String, bearer: String, json: String): HttpRequest {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $bearer")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        return request
    }

    fun sendAndGetStream(request: HttpRequest): InputStream {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream()).body()
    }
}
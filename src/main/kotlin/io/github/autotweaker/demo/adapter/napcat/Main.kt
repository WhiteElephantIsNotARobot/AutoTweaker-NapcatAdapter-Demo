package io.github.autotweaker.demo.adapter.napcat

import io.github.autotweaker.api.types.SemVer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("NapCatDemo")

fun main() {
    val adapter = NapCatAdapter()
    val info = adapter.load(SemVer(0, 1, 0))
    logger.info("Adapter loaded: {} v{}", info.name, info.version)
    logger.info("Description: {}", info.description)
    logger.info("To start the adapter, provide a CoreAPI instance via adapter.start(core)")
}

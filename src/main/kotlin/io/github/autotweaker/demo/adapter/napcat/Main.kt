package io.github.autotweaker.demo.adapter.napcat

import io.github.autotweaker.api.types.SemVer

fun main() {
    val adapter = NapCatAdapter()
    val info = adapter.load(SemVer(0, 1, 0))
    println("Adapter loaded: ${info.name} v${info.version}")
    println("Description: ${info.description}")
}

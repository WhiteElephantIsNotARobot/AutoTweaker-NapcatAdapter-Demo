package io.github.autotweaker.demo.adapter.napcat

import io.github.autotweaker.api.adapter.Adapter
import io.github.autotweaker.api.adapter.CoreAPI
import io.github.autotweaker.api.types.SemVer
import io.github.autotweaker.api.types.Url
import io.github.autotweaker.api.types.adapter.AdapterInfo

class NapCatAdapter : Adapter {
    private var core: CoreAPI? = null

    override fun load(coreVersion: SemVer): AdapterInfo {
        return AdapterInfo(
            name = "napcat",
            description = "NapCat adapter for AutoTweaker",
            version = SemVer(0, 1, 0, listOf("alpha")),
            source = Url("https://github.com/WhiteElephant-abc/AutoTweaker-NapcatAdapter-Demo")
        )
    }

    override fun start(core: CoreAPI) {
        this.core = core
        println("NapCat adapter started")
    }

    override fun stop() {
        this.core = null
        println("NapCat adapter stopped")
    }
}

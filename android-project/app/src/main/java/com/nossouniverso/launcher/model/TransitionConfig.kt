package com.nossouniverso.launcher.model

enum class HilmTransitionEffect(
    val id: String,
    val title: String,
    val description: String
) {
    PORTAL(
        id = "portal",
        title = "Portal holográfico",
        description = "Expansão em camadas concêntricas com profundidade ciano e laranja."
    ),
    DIGITAL_DISINTEGRATION(
        id = "digital-disintegration",
        title = "Desintegração digital",
        description = "Elementos se desfazem e se recompõem em micropartículas luminosas pontuais."
    ),
    LIGHT_SWEEP(
        id = "light-sweep",
        title = "Varredura de luz",
        description = "Uma faixa luminosa direcional revela painéis, telas e atalhos."
    ),
    DIMENSIONAL_SLIDE(
        id = "dimensional-slide",
        title = "Deslizamento dimensional",
        description = "Entrada e saída com perspectiva espacial, inclinação e profundidade."
    ),
    ENERGY_DISSOLVE(
        id = "energy-dissolve",
        title = "Dissolução energética",
        description = "Interface se dissolve e reaparece suavemente com onda de energia."
    ),
    CLASSIC(
        id = "classic",
        title = "Transição clássica",
        description = "Alternativa simples e leve com deslize e esmaecimento sutis."
    );

    companion object {
        fun fromId(raw: String?): HilmTransitionEffect {
            return values().firstOrNull { it.id == raw } ?: PORTAL
        }
    }
}

data class HilmTransitionConfig(
    val enabled: Boolean = true,
    val effect: HilmTransitionEffect = HilmTransitionEffect.PORTAL,
    val durationMs: Int = DEFAULT_DURATION_MS
) {
    fun sanitized(): HilmTransitionConfig {
        return copy(durationMs = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS))
    }

    companion object {
        const val MIN_DURATION_MS = 180
        const val MAX_DURATION_MS = 600
        const val DEFAULT_DURATION_MS = 280
    }
}

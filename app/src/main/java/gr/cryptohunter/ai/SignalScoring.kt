package gr.cryptohunter.ai

data class StrategyInputs(
    val emaTrend: Int,
    val rsiSetup: Int,
    val macdMomentum: Int,
    val volumeConfirm: Int,
    val supportResistance: Int,
    val multiTimeframe: Int
)

data class StrategyResult(
    val score: Int,
    val eligibleForAlert: Boolean,
    val reason: String
)

object SignalScoring {
    fun score(x: StrategyInputs): StrategyResult {
        val values = listOf(
            x.emaTrend,
            x.rsiSetup,
            x.macdMomentum,
            x.volumeConfirm,
            x.supportResistance,
            x.multiTimeframe
        )

        require(values.all { it in 0..100 })

        val score = (
            x.emaTrend * 0.20 +
            x.rsiSetup * 0.15 +
            x.macdMomentum * 0.15 +
            x.volumeConfirm * 0.15 +
            x.supportResistance * 0.15 +
            x.multiTimeframe * 0.20
        ).toInt()

        return StrategyResult(
            score = score,
            eligibleForAlert = score >= 80 &&
                x.multiTimeframe >= 70 &&
                x.volumeConfirm >= 60,
            reason = if (score >= 80)
                "Ισχυρή τεχνική συμφωνία — απαιτείται έλεγχος ρίσκου"
            else
                "Δεν πληρούνται τα κριτήρια ειδοποίησης"
        )
    }
}

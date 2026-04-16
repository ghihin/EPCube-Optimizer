package com.ghihin.epcubeoptimizer.domain.model

data class TargetSOC(
    val value: Int, // 20-100%
    val factors: CalculationFactors
) {
    init {
        require(value in 20..100) { "Target SOC must be between 20 and 100" }
    }
}

data class CalculationFactors(
    val shortwaveRadiationSum: Double,
    val isCommuteDay: Boolean
)

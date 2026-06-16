package com.fanta.androidsport.data.model

data class CourseItem(
    val id: String,
    val dateDebut: String,
    val distanceTotale: Double,
    val dureeSecondes: Double,
    val estBouclee: Boolean,
    val vitesseMoyenne: Double,
    val vitesseMax: Double,
    val allureMoyenne: Double,
    val caloriesEstimees: Double,
    val denivelePositif: Double,
    val deniveleNegatif: Double
)

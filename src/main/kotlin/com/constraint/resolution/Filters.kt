package com.constraint.resolution

fun callFilter(filterName: String?, depValue: Context?, pattern: Pattern) = when (filterName) {
    "greater" -> pattern.filter { it.id > depValue!!.id }.asSequence()
    else -> pattern.asSequence()
}

fun Pattern.filterBy(filterName: String?, depValue: Context?) = callFilter(filterName, depValue, this)

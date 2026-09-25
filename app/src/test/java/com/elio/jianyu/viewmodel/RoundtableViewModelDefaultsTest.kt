package com.elio.jianyu.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class RoundtableViewModelDefaultsTest {
    @Test
    fun defaultSessionRoles_prioritizeNavalAndFeynmanRegardlessOfCatalogOrder() {
        val actual = resolveDefaultSessionRoleIds(
            listOf(
                "zhang_xuefeng",
                "elon_musk",
                "richard_feynman",
                "naval_ravikant",
                "steve_jobs",
            ),
        )

        assertEquals(listOf("naval_ravikant", "richard_feynman"), actual)
    }

    @Test
    fun defaultSessionRoles_fallBackWhenPreferredRoleIsUnavailable() {
        val actual = resolveDefaultSessionRoleIds(
            listOf("zhang_xuefeng", "naval_ravikant", "steve_jobs"),
        )

        assertEquals(listOf("naval_ravikant", "zhang_xuefeng"), actual)
    }
}

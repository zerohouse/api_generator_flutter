package com.github.zerohouse.api_generator

import com.github.zerohouse.api_generator.test.UserController
import org.junit.Assert
import org.junit.Test


internal class ApiGeneratorTest {
    @Test
    fun defaultUrlParser() {
        Assert.assertEquals(
            ApiGenerator.defaultUrlParser(UserController::class.java.declaredMethods.first()),
            "\"/user\""
        )
    }
}
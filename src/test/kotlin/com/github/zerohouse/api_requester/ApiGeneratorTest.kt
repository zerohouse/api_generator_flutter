package com.github.zerohouse.api_requester

import com.github.zerohouse.api_requester.test.UserController
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
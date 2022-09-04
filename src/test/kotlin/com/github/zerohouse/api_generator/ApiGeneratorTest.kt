package com.github.zerohouse.api_generator

import com.github.zerohouse.api_generator.test.UserController
import org.junit.Assert
import org.junit.Test
import org.springframework.http.ResponseEntity
import java.lang.reflect.ParameterizedType


internal class ApiGeneratorTest {
    @Test
    fun defaultUrlParser() {
        Assert.assertEquals(
            ApiGenerator.defaultUrlParser(UserController::class.java.declaredMethods.first()),
            "\"/user\""
        )
    }

    @Test
    fun typeTest() {
        println(
            (UserController::class.java.declaredMethods.first().genericReturnType as ParameterizedType).rawType.typeName
        )
//        println(
//            ((UserController::class.java.declaredMethods.first().genericReturnType as ParameterizedType).actualTypeArguments[0] as ParameterizedType).actualTypeArguments[0]
//        )
    }
}
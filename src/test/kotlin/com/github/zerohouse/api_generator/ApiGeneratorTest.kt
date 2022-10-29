package com.github.zerohouse.api_generator

import com.github.zerohouse.api_generator.test.UserController
import org.junit.Assert
import org.junit.Test
import org.springframework.http.ResponseEntity
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


internal class ApiGeneratorTest {
    @Test
    fun defaultUrlParser() {
       ApiGenerator.generate("com.github.zerohouse.api_generator.test", "./")
    }

    @Test
    fun typeTest() {
        println(
            ApiGenerator.defaultReturnTypeParser(
                returnType(
                    (UserController::class.java.declaredMethods.first().genericReturnType as ParameterizedType).actualTypeArguments[0]
                )

            )
        )
    }

    fun returnType(returnType: Type): Type {
        if (returnType is ParameterizedType)
            if (listOf("ResponseEntity", "Mono", "Flux")
                    .contains(returnType.rawType.typeName.split(".").last())
            )
                return returnType(returnType.actualTypeArguments[0])
        return returnType
    }

}
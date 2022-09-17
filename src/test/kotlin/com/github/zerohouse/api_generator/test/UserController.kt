package com.github.zerohouse.api_generator.test

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/user")
class UserController {

    @GetMapping
    fun getUser(
        @RequestParam str: String,
        @RequestParam(defaultValue = "") str2: String,
        @RequestParam(required = false) str3: String?,
    ): List<Map<ABC, String>> {
        return mutableListOf()
    }
}

data class ABC(val a: String = "")
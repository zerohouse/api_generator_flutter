package com.github.zerohouse.api_generator.test

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
    ): Page<Map<ABC, Page<ABC>>> {
        return "" as Page<Map<ABC, Page<ABC>>>
    }
}

data class ABC(val a: String = "")

interface Page<T> {
}

@RestController
@RequestMapping("/user")
class User2Controller {

    @GetMapping
    fun getUser(
        @RequestParam str: String,
        @RequestParam(defaultValue = "") str2: String,
        @RequestParam(required = false) str3: String?,
    ): Page<Map<ABC, Page<ABC>>> {
        return "" as Page<Map<ABC, Page<ABC>>>
    }
}


@RestController
@RequestMapping("/user")
class User3Controller {

    @GetMapping
    fun getUser(
        @RequestParam str: String,
        @RequestParam(defaultValue = "") str2: String,
        @RequestParam(required = false) str3: String?,
    ): Page<Map<ABC, Page<ABC>>> {
        return "" as Page<Map<ABC, Page<ABC>>>
    }
}
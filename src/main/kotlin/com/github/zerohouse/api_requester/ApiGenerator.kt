package com.github.zerohouse.api_requester

import cz.habarta.typescript.generator.*
import org.apache.commons.io.FileUtils
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.springframework.web.bind.annotation.*
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

object ApiGenerator {

    private val defaultTypes = mutableMapOf(
        "boolean" to "boolean",
        "Boolean" to "boolean",
        "Long" to "number",
        "Integer" to "number",
        "int" to "number",
        "long" to "number",
        "Double" to "number",
        "double" to "number",
        "BigDecimal" to "number",
        "BigInteger" to "number",
        "String" to "string",
        "Date" to "Date",
        "List" to "%s[]",
        "ArrayList" to "%s[]",
        "Set" to "%s[]",
        "HashSet" to "%s[]",
        "Map" to "Map<%s>",
        "HashMap" to "Map<%s>",
        "Object" to "any"
    )

    private fun nameFrom(simpleName: String): String {
        val name = simpleName.split(".").last()
        if (defaultTypes.containsKey(name))
            return defaultTypes[name]!!
        return "TYPE.$name"
    }

    fun generate(
        packageName: String,
        path: String,
        typeNamer: (Type) -> String = {
            it.typeName.split(".").last()
        },
        parameterRequire: (Parameter) -> Boolean = {
            when {
                it.isAnnotationPresent(RequestParam::class.java) -> {
                    val annotation = it.getAnnotation(RequestParam::class.java)
                    annotation.defaultValue == ValueConstants.DEFAULT_NONE && annotation.required
                }
                else -> true
            }
        },
        parameterParser: (Parameter) -> String = {
            "${it.name}${if (parameterRequire(it)) "" else "?"}:${nameFrom(it.type.simpleName)}"
        },
        returnParser: (Type) -> String = {
            "Promise<${
                nameFrom(
                    it.typeName
                )
            }>"
        },
        urlParser: (Method) -> String = { method ->
            var url = (method.declaringClass.getAnnotation(RequestMapping::class.java).value.firstOrNull() ?: "") +
                    (method.getAnnotation(GetMapping::class.java).value.firstOrNull()
                        ?: method.getAnnotation(PostMapping::class.java).value.firstOrNull()
                        ?: method.getAnnotation(PutMapping::class.java).value.firstOrNull()
                        ?: method.getAnnotation(DeleteMapping::class.java).value.firstOrNull()
                        ?: method.getAnnotation(RequestMapping::class.java).value.firstOrNull())
            val pathParams = method.parameters.filter { it.isAnnotationPresent(PathVariable::class.java) }
            if (pathParams.isEmpty())
                "\"" + url + "\""
            else {
                method.parameters.filter { it.isAnnotationPresent(PathVariable::class.java) }
                    .forEach {
                        url = url.replace("{${it.name}}", "\${${it.name}}")
                    }
                "`$url`"
            }
        },
        methodParser: (Method) -> String = { method ->
            if (method.isAnnotationPresent(GetMapping::class.java))
                "get"
            else if (method.isAnnotationPresent(PostMapping::class.java))
                "post"
            else if (method.isAnnotationPresent(DeleteMapping::class.java))
                "delete"
            else if (method.isAnnotationPresent(PutMapping::class.java))
                "put"
            else
                ""
        },
        queryParamsParser: (Method) -> String = { method ->
            "{${
                method.parameters.filter { it.isAnnotationPresent(RequestParam::class.java) }
                    .joinToString(", ") { it.name }
            }}"
        },
        bodyParser: (Method) -> String = { method ->
            method.parameters.find { it.isAnnotationPresent(RequestBody::class.java) }?.name ?: "null"
        },
        returnFromGenericArgument: Boolean = false,
        annotations: List<Class<out Annotation>> = listOf(
            GetMapping::class.java,
            PostMapping::class.java,
            DeleteMapping::class.java,
            PutMapping::class.java
        )
    ) {
        val ref = Reflections(packageName, Scanners.MethodsAnnotated)
        val methodMap = mutableMapOf<Type, MutableList<Method>>()

        annotations.forEach {
            ref.getMethodsAnnotatedWith(it)
                .forEach {
                    if (methodMap[it.declaringClass] == null)
                        methodMap[it.declaringClass] = mutableListOf()
                    methodMap[it.declaringClass]!!.add(it)
                }
        }

        val result =
            TsGenerator(
                "ApiRequester",
                parameterRequire = parameterRequire,
                parameterParser = parameterParser,
                returnParser = returnParser,
                urlParser = urlParser,
                methodParser = methodParser,
                queryParamsParser = queryParamsParser,
                bodyParser = bodyParser,
                returnFromGenericArgument = returnFromGenericArgument
            ).apply {
                this.members = methodMap.map { it.key }.joinToString("\n") { type ->
                    typeNamer(type).replaceFirstChar { it.lowercase(Locale.getDefault()) } + " = new ${typeNamer(type)}(this.requester);"
                }
                this.preClass =
                    "import * as TYPE from './api.model'\n\nabstract class Requester {\n" +
                            "  abstract request(param: { queryParams: any; body: any; url: string, method: string }): Promise<any>\n" +
                            "}\n"
            }.makeResult() +
                    methodMap.map { kv ->
                        TsGenerator(
                            typeNamer(kv.key),
                            parameterRequire = parameterRequire,
                            parameterParser = parameterParser,
                            returnParser = returnParser,
                            urlParser = urlParser,
                            methodParser = methodParser,
                            queryParamsParser = queryParamsParser,
                            bodyParser = bodyParser,
                            returnFromGenericArgument = returnFromGenericArgument
                        ).apply {
                            kv.value.forEach { method ->
                                this.addMethods(method)
                            }
                        }.makeResult()
                    }.joinToString("\n\n")

        try {
            FileUtils.write(File("$path/api.requester.ts"), result, "utf8")
        } catch (e: IOException) {
            e.printStackTrace()
        }


        val types = mutableListOf<Type>()
        methodMap.map { it.value }.flatten().map {
            types.addAll(it.parameters.map { p -> p.parameterizedType })
            if (returnFromGenericArgument)
                types.add((it.genericReturnType as ParameterizedType).actualTypeArguments[0])
            else
                types.add(it.returnType)
        }

        makeTypeScriptModels(types, path)
    }


    private fun makeTypeScriptModels(types: MutableList<Type>, path: String) {
        TypeScriptGenerator(Settings().apply {
            outputKind = TypeScriptOutputKind.module
            jsonLibrary = JsonLibrary.jackson2
        }).generateTypeScript(
            Input.from(*types.toTypedArray()),
            Output.to(File("$path/api.model.ts"))
        )
    }


}
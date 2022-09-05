package com.github.zerohouse.api_generator

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
        "List" to "any[]",
        "ArrayList" to "any[]",
        "Set" to "any[]",
        "HashSet" to "any[]",
        "Map" to "Map<any, any>",
        "HashMap" to "Map<any, any>",
        "Object" to "any",
        "Void" to "void",
        "void" to "void"
    )

    private fun nameFrom(simpleName: String): String {
        val name = simpleName.split(".").last()
        if (defaultTypes.containsKey(name))
            return defaultTypes[name]!!
        return "TYPE.$name"
    }

    val defaultUrlParser: (Method) -> String = { method ->
        var url = (method.declaringClass.getAnnotation(RequestMapping::class.java)?.value?.firstOrNull() ?: "") +
                (method.getAnnotation(GetMapping::class.java)?.value?.firstOrNull()
                    ?: method.getAnnotation(PostMapping::class.java)?.value?.firstOrNull()
                    ?: method.getAnnotation(PutMapping::class.java)?.value?.firstOrNull()
                    ?: method.getAnnotation(DeleteMapping::class.java)?.value?.firstOrNull()
                    ?: method.getAnnotation(RequestMapping::class.java)?.value?.firstOrNull() ?: "")
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
    }

    val defaultTypeNamer: (Type) -> String = {
        it.typeName.split(".").last()
    }

    val defaultParameterTyper: (Parameter) -> ParameterType = {
        when {
            it.isAnnotationPresent(RequestParam::class.java) &&
                    it.getAnnotation(RequestParam::class.java).defaultValue == ValueConstants.DEFAULT_NONE
                    && it.getAnnotation(RequestParam::class.java).required -> {
                ParameterType.Required
            }
            it.isAnnotationPresent(RequestBody::class.java) && it.getAnnotation(RequestBody::class.java).required -> {
                ParameterType.Required
            }
            else -> ParameterType.Optional
        }
    }

    val defaultParameterParser: (Parameter) -> String = {
        "${it.name}${if (defaultParameterTyper(it) == ParameterType.Required) "" else "?"}:${nameFrom(it.type.simpleName)}"
    }

    val defaultReturnTypeParser: (Type) -> String = {
        "Promise<${nameFrom(it.typeName)}>"
    }

    val defaultHttpMethodParser: (Method) -> String = { method ->
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
    }

    val defaultQueryParamParser: (Method) -> String = { method ->
        "{${
            method.parameters.filter { it.isAnnotationPresent(RequestParam::class.java) }
                .joinToString(", ") { it.name }
        }}"
    }


    fun generate(
        packageName: String,
        path: String,
        typeNamer: (Type) -> String = defaultTypeNamer,
        parameterTyper: (Parameter) -> ParameterType = defaultParameterTyper,
        parameterParser: (Parameter) -> String = defaultParameterParser,
        returnParser: (Type) -> String = defaultReturnTypeParser,
        urlParser: (Method) -> String = defaultUrlParser,
        httpMethodParser: (Method) -> String = defaultHttpMethodParser,
        queryParamsParser: (Method) -> String = defaultQueryParamParser,
        bodyParser: (Method) -> String = { method ->
            method.parameters.find { it.isAnnotationPresent(RequestBody::class.java) }?.name ?: "null"
        },
        returnFromGenericArgument: Boolean = false,
        annotations: List<Class<out Annotation>> = listOf(
            GetMapping::class.java,
            PostMapping::class.java,
            DeleteMapping::class.java,
            PutMapping::class.java
        ),
        excludes: List<Class<*>> = listOf(),
        typeScriptModels: List<Type> = listOf(),
        modelFileName: String = "api.model",
        requesterClassName: String = "Requester"
    ) {

        val ref = Reflections(packageName, Scanners.MethodsAnnotated)
        val methodMap = mutableMapOf<Type, MutableList<Method>>()

        annotations.forEach { annotation ->
            ref.getMethodsAnnotatedWith(annotation)
                .forEach {
                    run {
                        if (it.declaringClass.isAnnotationPresent(ExcludeGeneration::class.java))
                            return@run
                        if (it.isAnnotationPresent(ExcludeGeneration::class.java))
                            return@run
                        if (methodMap[it.declaringClass] == null)
                            methodMap[it.declaringClass] = mutableListOf()
                        methodMap[it.declaringClass]!!.add(it)
                    }
                }
        }

        val result =
            TsGenerator(
                "Api$requesterClassName",
                parameterTyper = parameterTyper,
                parameterParser = parameterParser,
                returnParser = returnParser,
                urlParser = urlParser,
                methodParser = httpMethodParser,
                queryParamsParser = queryParamsParser,
                bodyParser = bodyParser,
                requesterClassName = requesterClassName
            ).apply {
                this.members = methodMap.map { it.key }.joinToString("\n") { type ->
                    typeNamer(type).replaceFirstChar { it.lowercase(Locale.getDefault()) } + " = new ${typeNamer(type)}(this.requester);"
                }
                this.preClass =
                    "import * as TYPE from './$modelFileName'\n\nabstract class $requesterClassName {\n" +
                            "  abstract request(param: { queryParams: any; body: any; url: string, method: string }): Promise<any>\n" +
                            "}\n"
            }.makeResult() +
                    methodMap.map { kv ->
                        TsGenerator(
                            typeNamer(kv.key),
                            parameterTyper = parameterTyper,
                            parameterParser = parameterParser,
                            returnParser = returnParser,
                            urlParser = urlParser,
                            methodParser = httpMethodParser,
                            queryParamsParser = queryParamsParser,
                            bodyParser = bodyParser,
                            requesterClassName = requesterClassName,
                        ).apply {
                            kv.value.forEach { method ->
                                this.addMethods(method)
                            }
                        }.makeResult()
                    }.joinToString("\n\n")

        try {
            FileUtils.write(File("$path/$requesterClassName.ts"), result, "utf8")
        } catch (e: IOException) {
            e.printStackTrace()
        }


        val types = typeScriptModels.toMutableList();
        methodMap.map { it.value }.flatten().map {
            types.addAll(it.parameters.filter { p -> !excludes.contains(p.type) }.map { p -> p.parameterizedType })
            if (returnFromGenericArgument)
                types.add((it.genericReturnType as ParameterizedType).actualTypeArguments[0])
            else
                types.add(it.returnType)
        }

        makeTypeScriptModels(types, path, modelFileName)
    }


    private fun makeTypeScriptModels(types: MutableList<Type>, path: String, modelFileName: String) {
        TypeScriptGenerator(Settings().apply {
            outputKind = TypeScriptOutputKind.module
            jsonLibrary = JsonLibrary.jackson2
        }).generateTypeScript(
            Input.from(*types.toTypedArray()),
            Output.to(File("$path/$modelFileName.ts"))
        )
    }


}


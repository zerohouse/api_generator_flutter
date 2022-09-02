package com.github.zerohouse.api_requester

import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


class TsGenerator(
    private var type: String,
    var constructor: String = "constructor(private requester: Requester) {\n    }",
    var members: String = "",
    val parameterRequire: (Parameter) -> Boolean,
    val parameterParser: (Parameter) -> String,
    val returnParser: (Type) -> String,
    val urlParser: (Method) -> String,
    val methodParser: (Method) -> String,
    val queryParamsParser: (Method) -> String,
    val bodyParser: (Method) -> String,
    val returnFromGenericArgument: Boolean,
    var preClass: String = "",
    vararg dependencies: String
) {

    var head = """
        export class %s {
        
        """.trimIndent()
    var end = "}"
    var methods = mutableListOf<Method>()
    var dependencies: MutableSet<String>? = LinkedHashSet()

    fun makeResult(): String {
        return """
$preClass
${String.format(head, className())}
    $constructor
    $members
    ${
            methods.joinToString("") {
                """${it.name}(${getParameters(it)}):${
                    this.returnParser(
                        returnTypeByMethod(it)
                    )
                }{
        return this.requester.request({method: "${this.methodParser(it)}", url:${this.urlParser(it)}, queryParams:${
                    this.queryParamsParser(
                        it
                    )
                }, body:${
                    this.bodyParser(
                        it
                    )
                }})
    }"""
            }
        }
$end""".trimIndent()
    }

    private fun getParameters(it: Method): String {
        val params = it.parameters.toMutableList()
        params.sortByDescending { p -> parameterRequire(p) }
        return params.joinToString(", ") { p -> this.parameterParser(p) }
    }

    private fun returnTypeByMethod(it: Method) = if (!returnFromGenericArgument) it.returnType else
        (it.genericReturnType as ParameterizedType).actualTypeArguments[0]

    private fun className(): String {
        return this.type
    }


    fun addImports(collect: String) {
        preClass += """
               
               $collect
               """.trimIndent()
    }

    fun addMethods(collect: Method) {
        methods += collect
    }

    fun addDependency(format: String) {
        dependencies!!.add(format)
    }

    fun saveResult(path: String) {
        val file = "${fileName()}.ts"
        try {
            FileUtils.write(File("$path/$file"), makeResult(), "utf8")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun fileName(): String {
        return this.type
    }

    init {
        this.dependencies!!.addAll(dependencies)
    }
}

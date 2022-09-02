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
            var url = (method.getAnnotation(GetMapping::class.java).value.firstOrNull() ?: "")
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
            types.add(it.returnType)
            types.add((it.genericReturnType as ParameterizedType).actualTypeArguments[0])
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

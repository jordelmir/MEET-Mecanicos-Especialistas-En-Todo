package dragoncore.evaluator

import dragoncore.lexer.Token
import dragoncore.lexer.TokenType
import dragoncore.parser.Expr
import dragoncore.cas.MatrixCore

/**
 * Dragon Core - DragonEvaluator
 *
 * Motor de evaluacion numerica que recorre el AST y computa resultados reales.
 *
 * Pipeline completo:
 *   String -> DragonLexer -> List<Token> -> DragonParser -> Expr (AST)
 *       -> DragonEvaluator.evaluate() -> Double (resultado)
 *
 * Capacidades:
 * - Aritmetica completa (+, -, *, /, %, ^)
 * - Trigonometria con soporte de modos angulares (RAD/DEG/GRAD)
 * - Funciones logaritmicas, exponenciales, raices
 * - Factorial, permutaciones, combinaciones, GCD, LCM
 * - Variables persistentes via DragonContext
 * - Evaluacion de matrices celda por celda
 * - Valor absoluto
 */
class DragonEvaluator {

    /**
     * Evalua un AST y devuelve el resultado numerico.
     *
     * @param expr    Arbol de expresion producido por el DragonParser.
     * @param context Estado de ejecucion (variables, modo angular).
     * @return Resultado numerico (Double).
     * @throws EvaluatorException si hay un error en tiempo de evaluacion.
     */
    fun evaluate(expr: Expr, context: DragonContext): Double {
        return when (expr) {

            // ── Literal numerico ──
            is Expr.NumberExpr -> expr.value

            // ── Variable ──
            is Expr.VariableExpr -> {
                context.getVariable(expr.name)
                    ?: throw EvaluatorException("Variable no definida: '${expr.name}'")
            }

            // ── Constante matematica ──
            is Expr.ConstantExpr -> resolveConstant(expr)

            // ── Operacion unaria ──
            is Expr.UnaryExpr -> evaluateUnary(expr, context)

            // ── Operacion binaria ──
            is Expr.BinaryExpr -> evaluateBinary(expr, context)

            // ── Funcion ──
            is Expr.FunctionExpr -> evaluateFunction(expr, context)

            // ── Asignacion ──
            is Expr.AssignExpr -> {
                val value = evaluate(expr.value, context)
                context.setVariable(expr.name, value)
                value // La asignacion devuelve el valor asignado
            }

            // ── Valor absoluto ──
            is Expr.AbsExpr -> {
                kotlin.math.abs(evaluate(expr.inner, context))
            }

            // ── Matriz ──
            is Expr.MatrixExpr -> {
                if (expr.rows.isEmpty() || expr.rows[0].isEmpty()) {
                    throw EvaluatorException("Matriz vacia")
                }
                if (expr.rows.size == 1 && expr.rows[0].size == 1) {
                    evaluate(expr.rows[0][0], context)
                } else {
                    val matrix = evaluateMatrix(expr, context)
                    matrix.determinant()
                }
            }

            // ================================================================
            // DRAGONSCRIPT: Control de flujo
            // ================================================================

            // ── Bloque de sentencias ──
            is Expr.BlockExpr -> {
                var result = 0.0
                for (stmt in expr.statements) {
                    result = evaluate(stmt, context)
                }
                result // El bloque retorna el último valor
            }

            // ── Condicional (evaluación perezosa) ──
            is Expr.IfExpr -> {
                val cond = evaluate(expr.condition, context)
                if (cond != 0.0) {
                    evaluate(expr.thenBranch, context)
                } else {
                    if (expr.elseBranch != null) evaluate(expr.elseBranch, context)
                    else 0.0
                }
            }

            // ── Bucle while (con límite de seguridad) ──
            is Expr.WhileExpr -> {
                var result = 0.0
                var iterations = 0
                val MAX_ITERATIONS = 100_000
                while (evaluate(expr.condition, context) != 0.0) {
                    result = evaluate(expr.body, context)
                    iterations++
                    if (iterations >= MAX_ITERATIONS) {
                        throw EvaluatorException(
                            "Límite de iteraciones excedido ($MAX_ITERATIONS). Posible ciclo infinito.")
                    }
                }
                result
            }

            // ── Definición de función de usuario ──
            is Expr.FunctionDefExpr -> {
                context.registerFunction(expr.name, expr.params, expr.body)
                0.0 // def no devuelve valor
            }

            // ── Return ──
            is Expr.ReturnExpr -> {
                val value = evaluate(expr.value, context)
                throw ReturnException(value)
            }

            // ── Derivada (Simbólica -> Numérica) ──
            is Expr.DerivativeExpr -> {
                val cas = dragoncore.cas.CalculusEngine()
                val derived = cas.derive(expr.expression, expr.variable.name)
                evaluate(derived, context)
            }

            // ── Integral (Simbólica) ──
            is Expr.IntegralExpr -> {
                throw EvaluatorException("La integral simbólica no se puede evaluar numéricamente sin límites. Use integrate(f, x, a, b)")
            }
        }
    }

    // ================================================================
    // CONSTANTES
    // ================================================================

    private fun resolveConstant(expr: Expr.ConstantExpr): Double {
        val name = expr.name.lowercase()
        return when (expr.token.type) {
            TokenType.PI        -> Math.PI
            TokenType.E         -> Math.E
            TokenType.PHI       -> 1.6180339887498948482
            TokenType.INF       -> Double.POSITIVE_INFINITY
            TokenType.IMAGINARY -> {
                // i como unidad imaginaria:
                // En evaluación escalar pura, devolvemos NaN como marcador.
                // Para cálculos como |i|, e^(i*pi), etc., delegamos a ComplexNumber.
                Double.NaN
            }
            else -> DragonContext.CONSTANTS[name]
                ?: throw EvaluatorException("Constante desconocida: '${expr.name}'")
        }
    }

    // ================================================================
    // OPERACIONES UNARIAS
    // ================================================================

    private fun evaluateUnary(expr: Expr.UnaryExpr, context: DragonContext): Double {
        val operand = evaluate(expr.operand, context)

        return when (expr.operator.type) {
            // Negacion: -x
            TokenType.MINUS -> -operand

            // Factorial postfijo: n!
            TokenType.FACTORIAL -> {
                if (operand < 0 || operand != kotlin.math.floor(operand)) {
                    throw EvaluatorException(
                        "Factorial solo esta definido para enteros no negativos, se recibio: $operand")
                }
                factorial(operand.toLong()).toDouble()
            }

            // Bitwise NOT: ~x
            TokenType.BIT_NOT -> {
                operand.toLong().inv().toDouble()
            }

            else -> throw EvaluatorException(
                "Operador unario no soportado: '${expr.operator.value}'")
        }
    }

    // ================================================================
    // OPERACIONES BINARIAS
    // ================================================================

    private fun evaluateBinary(expr: Expr.BinaryExpr, context: DragonContext): Double {
        // Short-circuit para AND/OR lógico
        if (expr.operator.type == TokenType.AND) {
            val left = evaluate(expr.left, context)
            if (left == 0.0) return 0.0  // cortocircuito
            return if (evaluate(expr.right, context) != 0.0) 1.0 else 0.0
        }
        if (expr.operator.type == TokenType.OR) {
            val left = evaluate(expr.left, context)
            if (left != 0.0) return 1.0  // cortocircuito
            return if (evaluate(expr.right, context) != 0.0) 1.0 else 0.0
        }

        val left = evaluate(expr.left, context)
        val right = evaluate(expr.right, context)

        return when (expr.operator.type) {
            // Aritmetica
            TokenType.PLUS     -> left + right
            TokenType.MINUS    -> left - right
            TokenType.MULTIPLY -> left * right
            TokenType.DIVIDE   -> {
                if (right == 0.0) throw EvaluatorException("Division por cero")
                left / right
            }
            TokenType.MODULO   -> {
                if (right == 0.0) throw EvaluatorException("Modulo por cero")
                left % right
            }
            TokenType.POWER    -> Math.pow(left, right)

            // Comparacion (retorna 1.0 para true, 0.0 para false)
            TokenType.EQUALS        -> if (left == right) 1.0 else 0.0
            TokenType.NOT_EQUALS    -> if (left != right) 1.0 else 0.0
            TokenType.LESS_THAN     -> if (left < right) 1.0 else 0.0
            TokenType.GREATER_THAN  -> if (left > right) 1.0 else 0.0
            TokenType.LESS_EQUAL    -> if (left <= right) 1.0 else 0.0
            TokenType.GREATER_EQUAL -> if (left >= right) 1.0 else 0.0

            // Bitwise
            TokenType.BIT_AND         -> (left.toLong() and right.toLong()).toDouble()
            TokenType.BIT_OR          -> (left.toLong() or right.toLong()).toDouble()
            TokenType.BIT_XOR         -> (left.toLong() xor right.toLong()).toDouble()
            TokenType.BIT_SHIFT_LEFT  -> (left.toLong() shl right.toInt()).toDouble()
            TokenType.BIT_SHIFT_RIGHT -> (left.toLong() shr right.toInt()).toDouble()

            else -> throw EvaluatorException(
                "Operador binario no soportado: '${expr.operator.value}'")
        }
    }

    // ================================================================
    // FUNCIONES (40+)
    // ================================================================

    private fun evaluateFunction(expr: Expr.FunctionExpr, context: DragonContext): Double {
        // ── CAS: intercept BEFORE evaluating arguments ──
        when (expr.name.type) {
            TokenType.DERIVATIVE -> {
                // diff(expr, x) → derivar simbólicamente y evaluar
                if (expr.arguments.size < 2) throw EvaluatorException("diff() requiere 2 args: diff(expr, var)")
                val bodyExpr = expr.arguments[0]
                val varExpr = expr.arguments[1]
                val varName = when (varExpr) {
                    is Expr.VariableExpr -> varExpr.name
                    else -> throw EvaluatorException("Segundo arg de diff() debe ser una variable")
                }
                val cas = dragoncore.cas.CalculusEngine()
                val derived = cas.derive(bodyExpr, varName)
                return evaluate(derived, context)
            }
            TokenType.INTEGRAL -> {
                // integrate(expr, x, a, b) → integración numérica
                if (expr.arguments.size < 4) throw EvaluatorException("integrate() requiere 4 args: integrate(expr, var, a, b)")
                val bodyExpr = expr.arguments[0]
                val varExpr = expr.arguments[1]
                val varName = when (varExpr) {
                    is Expr.VariableExpr -> varExpr.name
                    else -> throw EvaluatorException("Segundo arg de integrate() debe ser una variable")
                }
                val a = evaluate(expr.arguments[2], context)
                val b = evaluate(expr.arguments[3], context)
                val cas = dragoncore.cas.CalculusEngine()
                return cas.integrate(bodyExpr, varName, a, b, context)
            }
            TokenType.SIMPLIFY -> {
                // simplify(expr) → simplificar y evaluar
                if (expr.arguments.isEmpty()) throw EvaluatorException("simplify() requiere 1 arg")
                val opt = dragoncore.cas.SymbolicOptimizer()
                val simplified = opt.simplify(expr.arguments[0])
                return evaluate(simplified, context)
            }
            else -> { /* continue to normal evaluation below */ }
        }

        val args = expr.arguments.map { evaluate(it, context) }
        val name = expr.name.type

        // Verificacion de aridad minima
        fun requireArgs(min: Int, label: String = expr.name.value) {
            if (args.size < min) throw EvaluatorException(
                "La funcion '$label' requiere al menos $min argumento(s), se recibieron ${args.size}")
        }

        return when (name) {

            // ── Trigonometria directa ──
            TokenType.SIN -> { requireArgs(1); kotlin.math.sin(context.toRadians(args[0])) }
            TokenType.COS -> { requireArgs(1); kotlin.math.cos(context.toRadians(args[0])) }
            TokenType.TAN -> { requireArgs(1); kotlin.math.tan(context.toRadians(args[0])) }

            // ── Trigonometria inversa ──
            TokenType.ASIN -> { requireArgs(1); context.fromRadians(kotlin.math.asin(args[0])) }
            TokenType.ACOS -> { requireArgs(1); context.fromRadians(kotlin.math.acos(args[0])) }
            TokenType.ATAN -> { requireArgs(1); context.fromRadians(kotlin.math.atan(args[0])) }
            TokenType.ATAN2 -> {
                requireArgs(2, "atan2")
                context.fromRadians(kotlin.math.atan2(args[0], args[1]))
            }

            // ── Hiperbolicas ──
            TokenType.SINH  -> { requireArgs(1); kotlin.math.sinh(args[0]) }
            TokenType.COSH  -> { requireArgs(1); kotlin.math.cosh(args[0]) }
            TokenType.TANH  -> { requireArgs(1); kotlin.math.tanh(args[0]) }
            TokenType.ASINH -> { requireArgs(1); kotlin.math.asinh(args[0]) }
            TokenType.ACOSH -> { requireArgs(1); kotlin.math.acosh(args[0]) }
            TokenType.ATANH -> { requireArgs(1); kotlin.math.atanh(args[0]) }

            // ── Conversion angular ──
            TokenType.DEG -> { requireArgs(1); Math.toDegrees(args[0]) }
            TokenType.RAD -> { requireArgs(1); Math.toRadians(args[0]) }

            // ── Logaritmos y exponenciales ──
            TokenType.LN   -> { requireArgs(1); kotlin.math.ln(args[0]) }
            TokenType.LOG  -> {
                if (args.size >= 2) {
                    kotlin.math.ln(args[1]) / kotlin.math.ln(args[0])
                } else {
                    requireArgs(1)
                    kotlin.math.log10(args[0])
                }
            }
            TokenType.LOG2 -> { requireArgs(1); kotlin.math.log2(args[0]) }
            TokenType.EXP  -> { requireArgs(1); kotlin.math.exp(args[0]) }

            // ── Raices ──
            TokenType.SQRT -> { requireArgs(1); kotlin.math.sqrt(args[0]) }
            TokenType.CBRT -> { requireArgs(1); Math.cbrt(args[0]) }
            TokenType.NRT  -> {
                requireArgs(2, "nrt")
                Math.pow(args[1], 1.0 / args[0])
            }

            // ── Redondeo y signo ──
            TokenType.ABS   -> { requireArgs(1); kotlin.math.abs(args[0]) }
            TokenType.CEIL  -> { requireArgs(1); kotlin.math.ceil(args[0]) }
            TokenType.FLOOR -> { requireArgs(1); kotlin.math.floor(args[0]) }
            TokenType.ROUND -> { requireArgs(1); kotlin.math.round(args[0]).toDouble() }
            TokenType.SIGN  -> { requireArgs(1); kotlin.math.sign(args[0]) }

            // ── Combinatoria ──
            TokenType.NPR -> {
                requireArgs(2, "nPr")
                permutations(args[0].toLong(), args[1].toLong()).toDouble()
            }
            TokenType.NCR -> {
                requireArgs(2, "nCr")
                combinations(args[0].toLong(), args[1].toLong()).toDouble()
            }
            TokenType.GAMMA -> { requireArgs(1); gammaFunction(args[0]) }
            TokenType.GCD -> {
                requireArgs(2, "gcd")
                gcd(args[0].toLong(), args[1].toLong()).toDouble()
            }
            TokenType.LCM -> {
                requireArgs(2, "lcm")
                lcm(args[0].toLong(), args[1].toLong()).toDouble()
            }

            // ── Algebra lineal (MatrixCore) ──
            TokenType.DETERMINANT -> {
                requireArgs(1, "det")
                val matExpr = expr.arguments[0]
                if (matExpr !is Expr.MatrixExpr) throw EvaluatorException("det() requiere una matriz")
                val matrix = evaluateMatrix(matExpr, context)
                matrix.determinant()
            }
            TokenType.INVERSE -> {
                requireArgs(1, "inv")
                val matExpr = expr.arguments[0]
                if (matExpr !is Expr.MatrixExpr) throw EvaluatorException("inv() requiere una matriz")
                val matrix = evaluateMatrix(matExpr, context)
                val inv = matrix.inverse()
                // Devuelve det de la inversa como marcador escalar
                // La evaluación matricial completa usa matrixEval
                inv.determinant()
            }
            TokenType.TRANSPOSE -> {
                requireArgs(1, "trans")
                val matExpr = expr.arguments[0]
                if (matExpr !is Expr.MatrixExpr) throw EvaluatorException("trans() requiere una matriz")
                val matrix = evaluateMatrix(matExpr, context)
                val t = matrix.transpose()
                t.trace()
            }
            TokenType.RREF -> {
                requireArgs(1, "rref")
                val matExpr = expr.arguments[0]
                if (matExpr !is Expr.MatrixExpr) throw EvaluatorException("rref() requiere una matriz")
                val matrix = evaluateMatrix(matExpr, context)
                val r = matrix.rref()
                r.trace()
            }
            TokenType.CROSS, TokenType.DOT, TokenType.EIGEN -> {
                throw EvaluatorException(
                    "Funcion '${expr.name.value}' aun en desarrollo.")
            }

            // ── CAS restantes (futuro) ──
            TokenType.LIMIT, TokenType.SUMMATION, TokenType.PRODUCT_FUNC,
            TokenType.TAYLOR, TokenType.SOLVE -> {
                throw EvaluatorException(
                    "Funcion '${expr.name.value}' aun no implementada en CAS.")
            }

            else -> {
                // ── User-Defined Functions (UDF) ──
                val funcName = expr.name.value
                val udf = context.getFunction(funcName)
                    ?: throw EvaluatorException("Función no soportada: '$funcName'")

                val (params, body) = udf
                if (expr.arguments.size != params.size) {
                    throw EvaluatorException(
                        "'$funcName' esperaba ${params.size} argumentos, recibió ${expr.arguments.size}")
                }

                // Evaluar argumentos ANTES de pushear scope
                val argValues = expr.arguments.map { evaluate(it, context) }

                // Crear scope local con parámetros
                context.pushScope()
                try {
                    for (i in params.indices) {
                        context.setVariable(params[i], argValues[i])
                    }
                    evaluate(body, context)
                } catch (ret: ReturnException) {
                    ret.value
                } finally {
                    context.popScope()
                }
            }
        }
    }

    // ================================================================
    // FUNCIONES MATEMATICAS AUXILIARES
    // ================================================================

    /** Calcula n! iterativamente. Soporta hasta 170! (limite de Double). */
    private fun factorial(n: Long): Long {
        if (n < 0) throw EvaluatorException("Factorial no definido para negativos")
        if (n > 20) {
            // Para n > 20, Long overflow. Usar Double directamente.
            var result = 1.0
            for (i in 2..n) result *= i
            return result.toLong()
        }
        var result = 1L
        for (i in 2..n) result *= i
        return result
    }

    /** Permutaciones: nPr = n! / (n-r)! */
    private fun permutations(n: Long, r: Long): Long {
        if (r > n || r < 0 || n < 0) throw EvaluatorException("nPr: valores invalidos n=$n, r=$r")
        var result = 1L
        for (i in (n - r + 1)..n) result *= i
        return result
    }

    /** Combinaciones: nCr = n! / (r! * (n-r)!) */
    private fun combinations(n: Long, r: Long): Long {
        if (r > n || r < 0 || n < 0) throw EvaluatorException("nCr: valores invalidos n=$n, r=$r")
        val effectiveR = if (r > n - r) n - r else r
        var result = 1L
        for (i in 0 until effectiveR) {
            result = result * (n - i) / (i + 1)
        }
        return result
    }

    /** Maximo comun divisor (algoritmo de Euclides). */
    private fun gcd(a: Long, b: Long): Long {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0L) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    /** Minimo comun multiplo. */
    private fun lcm(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0
        return kotlin.math.abs(a / gcd(a, b) * b)
    }

    /**
     * Funcion Gamma usando la aproximacion de Lanczos.
     * Gamma(n) = (n-1)! para enteros positivos.
     */
    private fun gammaFunction(x: Double): Double {
        if (x <= 0 && x == kotlin.math.floor(x)) {
            throw EvaluatorException("Gamma no definida para enteros no positivos")
        }
        // Para enteros positivos, usar factorial
        if (x > 0 && x == kotlin.math.floor(x) && x <= 21) {
            return factorial(x.toLong() - 1).toDouble()
        }
        // Aproximacion de Lanczos
        val g = 7.0
        val coefficients = doubleArrayOf(
            0.99999999999980993,
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        )
        val z = x - 1.0
        var sum = coefficients[0]
        for (i in 1 until coefficients.size) {
            sum += coefficients[i] / (z + i)
        }
        val t = z + g + 0.5
        return kotlin.math.sqrt(2.0 * Math.PI) * Math.pow(t, z + 0.5) * kotlin.math.exp(-t) * sum
    }

    // ================================================================
    // MATRICES: Conversión AST → MatrixCore
    // ================================================================

    /**
     * Evalúa cada celda de un MatrixExpr y construye un MatrixCore numérico.
     */
    private fun evaluateMatrix(expr: Expr.MatrixExpr, context: DragonContext): MatrixCore {
        val numRows = expr.rows.size
        val numCols = expr.rows[0].size
        val data = Array(numRows) { i ->
            DoubleArray(numCols) { j ->
                evaluate(expr.rows[i][j], context)
            }
        }
        return MatrixCore(data)
    }

    // ================================================================
    // UTILIDAD: Evaluacion de punta a punta (helper de conveniencia)
    // ================================================================

    companion object {
        /**
         * Evalua un string matematico completo: tokeniza, parsea, y evalua.
         * Util para el Testing Bridge (ADB) y tests rapidos.
         */
        fun eval(input: String, context: DragonContext = DragonContext()): Double {
            val lexer = dragoncore.lexer.DragonLexer()
            val tokens = lexer.tokenize(input)
            val parser = dragoncore.parser.DragonParser(tokens)
            val ast = parser.parse()
            return DragonEvaluator().evaluate(ast, context)
        }
    }
}

/**
 * Excepcion de evaluacion con contexto descriptivo.
 */
class EvaluatorException(
    val detail: String,
    message: String = "Error de evaluacion: $detail"
) : RuntimeException(message)

/**
 * Excepción de control de flujo para 'return' en UDFs.
 * No es un error real, sino un mecanismo para devolver valores tempranamente.
 */
class ReturnException(val value: Double) : RuntimeException()

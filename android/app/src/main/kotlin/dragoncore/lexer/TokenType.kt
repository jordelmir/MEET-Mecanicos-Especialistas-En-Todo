package dragoncore.lexer

/**
 * 🐉 Dragon Core — TokenType
 *
 * Enum exhaustivo que define cada tipo de token reconocido por el Lexer.
 * Organizado por categorías funcionales para facilitar la extensión
 * cuando se implementen nuevos módulos (CAS, Álgebra Lineal, etc.).
 */
enum class TokenType {

    // ─────────────────────────────────────────────
    // LITERALES
    // ─────────────────────────────────────────────
    /** Número entero o decimal (ej. 42, 3.1415, .5) */
    NUMBER,

    // ─────────────────────────────────────────────
    // OPERADORES ARITMÉTICOS
    // ─────────────────────────────────────────────
    PLUS,           // +
    MINUS,          // -
    MULTIPLY,       // *
    DIVIDE,         // /
    POWER,          // ^
    MODULO,         // %
    FACTORIAL,      // !

    // ─────────────────────────────────────────────
    // OPERADORES DE COMPARACIÓN
    // ─────────────────────────────────────────────
    EQUALS,         // ==
    NOT_EQUALS,     // !=
    LESS_THAN,      // <
    GREATER_THAN,   // >
    LESS_EQUAL,     // <=
    GREATER_EQUAL,  // >=

    // ─────────────────────────────────────────────
    // FUNCIONES TRIGONOMÉTRICAS
    // ─────────────────────────────────────────────
    SIN,            // sin(x)
    COS,            // cos(x)
    TAN,            // tan(x)
    ASIN,           // asin(x) — arcoseno
    ACOS,           // acos(x) — arcocoseno
    ATAN,           // atan(x) — arcotangente
    ATAN2,          // atan2(y, x) — arcotangente de dos argumentos
    SINH,           // sinh(x) — seno hiperbólico
    COSH,           // cosh(x) — coseno hiperbólico
    TANH,           // tanh(x) — tangente hiperbólica
    ASINH,          // asinh(x)
    ACOSH,          // acosh(x)
    ATANH,          // atanh(x)
    /** Conversión grados → radianes */
    DEG,
    /** Conversión radianes → grados */
    RAD,

    // --- Recíprocos ---
    SEC,            // sec(x)
    CSC,            // csc(x)
    COT,            // cot(x)

    // ─────────────────────────────────────────────
    // FUNCIONES LOGARÍTMICAS / EXPONENCIALES
    // ─────────────────────────────────────────────
    LN,             // ln(x) — logaritmo natural
    LOG,            // log(x) — logaritmo base 10
    LOG2,           // log2(x) — logaritmo base 2
    EXP,            // exp(x) — e^x
    SQRT,           // sqrt(x) — raíz cuadrada
    CBRT,           // cbrt(x) — raíz cúbica
    ABS,            // abs(x) — valor absoluto
    CEIL,           // ceil(x) — techo
    FLOOR,          // floor(x) — piso
    ROUND,          // round(x) — redondeo
    SIGN,           // sign(x) — signo (-1, 0, 1)
    /** Raíz n-ésima: nrt(n, x) */
    NRT,

    // ─────────────────────────────────────────────
    // FUNCIONES COMBINATORIAS
    // ─────────────────────────────────────────────
    /** Permutaciones nPr */
    NPR,
    /** Combinaciones nCr */
    NCR,
    /** Función gamma Γ(x) */
    GAMMA,
    /** Máximo común divisor */
    GCD,
    /** Mínimo común múltiplo */
    LCM,

    // ─────────────────────────────────────────────
    // CÁLCULO CAS (Computer Algebra System)
    // ─────────────────────────────────────────────
    /** ∫ — Integral definida/indefinida */
    INTEGRAL,
    /** d/dx — Derivada */
    DERIVATIVE,
    /** lim — Límite */
    LIMIT,
    /** Σ — Sumatoria */
    SUMMATION,
    /** Π — Productoria */
    PRODUCT_FUNC,
    /** Serie de Taylor */
    TAYLOR,
    /** Resolver ecuación (Newton-Raphson, etc.) */
    SOLVE,
    /** Simplificar expresión simbólica */
    SIMPLIFY,

    // ─────────────────────────────────────────────
    // ÁLGEBRA LINEAL
    // ─────────────────────────────────────────────
    /** [ — Inicio de matriz/vector */
    MATRIX_START,
    /** ] — Fin de matriz/vector */
    MATRIX_END,
    /** det() — Determinante */
    DETERMINANT,
    /** trans() — Transposición */
    TRANSPOSE,
    /** inv() — Inversa */
    INVERSE,
    /** rref() — Forma escalonada reducida */
    RREF,
    /** cross() — Producto cruz */
    CROSS,
    /** dot() — Producto punto */
    DOT,
    /** eigen() — Autovalores/autovectores */
    EIGEN,

    // ─────────────────────────────────────────────
    // CONSTANTES MATEMÁTICAS
    // ─────────────────────────────────────────────
    /** π ≈ 3.14159265358979... */
    PI,
    /** e ≈ 2.71828182845904... */
    E,
    /** φ (golden ratio) ≈ 1.61803398874989... */
    PHI,
    /** ∞ */
    INF,
    /** Unidad imaginaria i */
    IMAGINARY,
    /** Constante de Avogadro, Boltzmann, etc. — resueltas por lookup */
    CONSTANT,

    // ─────────────────────────────────────────────
    // VARIABLES
    // ─────────────────────────────────────────────
    /** Variable simbólica (x, y, z, t, n, θ, etc.) */
    VARIABLE,

    // ─────────────────────────────────────────────
    // DELIMITADORES Y PUNTUACIÓN
    // ─────────────────────────────────────────────
    LPAREN,         // (
    RPAREN,         // )
    COMMA,          // ,
    SEMICOLON,      // ;
    /** | — usado para valor absoluto o determinante */
    PIPE,

    // ─────────────────────────────────────────────
    // LITERALES BASE-N
    // ─────────────────────────────────────────────
    /** 0x... — Hexadecimal */
    HEX_LITERAL,
    /** 0b... — Binario */
    BIN_LITERAL,
    /** 0o... — Octal */
    OCT_LITERAL,

    // ─────────────────────────────────────────────
    // OPERADORES BIT A BIT
    // ─────────────────────────────────────────────
    BIT_AND,        // &
    BIT_OR,         // | (contextual con PIPE)
    BIT_XOR,        // @ usamos @ para XOR bit a bit
    BIT_NOT,        // ~
    BIT_SHIFT_LEFT, // <<
    BIT_SHIFT_RIGHT,// >>

    // ─────────────────────────────────────────────
    // ASIGNACIÓN
    // ─────────────────────────────────────────────
    /** = — Asignación de variable */
    ASSIGN,

    // ─────────────────────────────────────────────
    // DRAGONSCRIPT (Control de flujo)
    // ─────────────────────────────────────────────
    /** if — Condicional */
    IF,
    /** else — Rama alternativa */
    ELSE,
    /** while — Bucle iterativo */
    WHILE,
    /** def — Definición de función de usuario */
    DEF,
    /** return — Retorno explícito */
    RETURN,
    /** && — AND lógico */
    AND,
    /** || — OR lógico */
    OR,
    /** { — Inicio de bloque */
    LBRACE,
    /** } — Fin de bloque */
    RBRACE,
    /** \n o ; — Separador de sentencias */
    NEWLINE,

    // ─────────────────────────────────────────────
    // ESPECIALES
    // ─────────────────────────────────────────────
    /** Marca fin de input */
    EOF,
    /** Token no reconocido (para reporte de errores) */
    UNKNOWN
}

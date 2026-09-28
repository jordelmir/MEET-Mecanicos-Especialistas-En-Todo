package cr.dragon.calc.ui.keyboard

enum class BtnType {
    NUM, OP, FUNC, SPECIAL, EQUAL, NAV
}

data class CalcBtn(
    val label: String,
    val input: String = label,
    val type: BtnType = BtnType.NUM
)

object DragonKeyboardLayouts {

    val NUMERIC = listOf(
        CalcBtn("AC", "", BtnType.SPECIAL),
        CalcBtn("DEL", "", BtnType.SPECIAL),
        CalcBtn("(", "(", BtnType.OP),
        CalcBtn(")", ")", BtnType.OP),

        CalcBtn("7"), CalcBtn("8"), CalcBtn("9"),
        CalcBtn("\u00F7", " / ", BtnType.OP),

        CalcBtn("4"), CalcBtn("5"), CalcBtn("6"),
        CalcBtn("\u00D7", " * ", BtnType.OP),

        CalcBtn("1"), CalcBtn("2"), CalcBtn("3"),
        CalcBtn("-", " - ", BtnType.OP),

        CalcBtn("0"), CalcBtn("."),
        CalcBtn("=", "", BtnType.EQUAL),
        CalcBtn("+", " + ", BtnType.OP)
    )

    val SCIENTIFIC = listOf(
        CalcBtn("\u2190", "LEFT", BtnType.NAV), // Left arrow
        CalcBtn("\u2192", "RIGHT", BtnType.NAV), // Right arrow
        CalcBtn("%", " % ", BtnType.OP),
        CalcBtn("^", " ^ ", BtnType.OP),

        CalcBtn("sin", "sin(", BtnType.FUNC),
        CalcBtn("cos", "cos(", BtnType.FUNC),
        CalcBtn("tan", "tan(", BtnType.FUNC),
        CalcBtn("!", "!", BtnType.OP),

        CalcBtn("asin", "asin(", BtnType.FUNC),
        CalcBtn("acos", "acos(", BtnType.FUNC),
        CalcBtn("atan", "atan(", BtnType.FUNC),
        CalcBtn("\u221A", "sqrt(", BtnType.FUNC),

        CalcBtn("ln", "ln(", BtnType.FUNC),
        CalcBtn("log", "log(", BtnType.FUNC),
        CalcBtn("\u03C0", "pi", BtnType.FUNC),
        CalcBtn("e", "e", BtnType.FUNC)
    )

    val MATRIX = listOf(
        CalcBtn("\u2190", "LEFT", BtnType.NAV),
        CalcBtn("\u2192", "RIGHT", BtnType.NAV),
        CalcBtn("det", "det(", BtnType.FUNC),
        CalcBtn("inv", "inv(", BtnType.FUNC),
        CalcBtn("tr", "tr(", BtnType.FUNC),
        CalcBtn("eig", "eig(", BtnType.FUNC),

        CalcBtn("[", "[", BtnType.OP),
        CalcBtn("]", "]", BtnType.OP),
        CalcBtn(",", ", ", BtnType.OP),
        CalcBtn(";", "; ", BtnType.OP),

        CalcBtn("solve", "solve(", BtnType.FUNC),
        CalcBtn("rref", "rref(", BtnType.FUNC),
        CalcBtn("T", "^T", BtnType.OP),
        CalcBtn("I", "I(", BtnType.FUNC),
        CalcBtn("0", "0", BtnType.NUM)
    )

    val SCRIPT = listOf(
        CalcBtn("if", "if(", BtnType.FUNC),
        CalcBtn("else", " else ", BtnType.FUNC),
        CalcBtn("for", "for ", BtnType.FUNC),
        CalcBtn("in", " in ", BtnType.OP),

        CalcBtn("while", "while(", BtnType.FUNC),
        CalcBtn("range", "range(", BtnType.FUNC),
        CalcBtn("def", "def ", BtnType.FUNC),
        CalcBtn("ret", "return ", BtnType.FUNC),

        CalcBtn("print", "print(", BtnType.FUNC),
        CalcBtn(":", ": ", BtnType.OP),
        CalcBtn("{", "{ ", BtnType.OP),
        CalcBtn("}", " }", BtnType.OP),

        CalcBtn("==", " == ", BtnType.OP),
        CalcBtn("!=", " != ", BtnType.OP),
        CalcBtn("&&", " && ", BtnType.OP),
        CalcBtn("||", " || ", BtnType.OP),

        CalcBtn("<", " < ", BtnType.OP),
        CalcBtn(">", " > ", BtnType.OP),
        CalcBtn("<=", " <= ", BtnType.OP),
        CalcBtn(">=", " >= ", BtnType.OP)
    )
}

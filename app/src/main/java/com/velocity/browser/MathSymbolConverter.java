package com.velocity.browser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MathSymbolConverter {

    private static final Map<String, String> LATEX_SYMBOLS = new HashMap<>();
    private static final Map<Character, Character> SUPERSCRIPTS = new HashMap<>();
    private static final Map<Character, Character> SUBSCRIPTS = new HashMap<>();

    static {
        // Lowercase Greek letters
        LATEX_SYMBOLS.put("alpha", "α");
        LATEX_SYMBOLS.put("beta", "β");
        LATEX_SYMBOLS.put("gamma", "γ");
        LATEX_SYMBOLS.put("delta", "δ");
        LATEX_SYMBOLS.put("epsilon", "ε");
        LATEX_SYMBOLS.put("varepsilon", "ε");
        LATEX_SYMBOLS.put("zeta", "ζ");
        LATEX_SYMBOLS.put("eta", "η");
        LATEX_SYMBOLS.put("theta", "θ");
        LATEX_SYMBOLS.put("vartheta", "ϑ");
        LATEX_SYMBOLS.put("iota", "ι");
        LATEX_SYMBOLS.put("kappa", "κ");
        LATEX_SYMBOLS.put("lambda", "λ");
        LATEX_SYMBOLS.put("mu", "μ");
        LATEX_SYMBOLS.put("nu", "ν");
        LATEX_SYMBOLS.put("xi", "ξ");
        LATEX_SYMBOLS.put("pi", "π");
        LATEX_SYMBOLS.put("varpi", "ϖ");
        LATEX_SYMBOLS.put("rho", "ρ");
        LATEX_SYMBOLS.put("varrho", "ϱ");
        LATEX_SYMBOLS.put("sigma", "σ");
        LATEX_SYMBOLS.put("varsigma", "ς");
        LATEX_SYMBOLS.put("tau", "τ");
        LATEX_SYMBOLS.put("upsilon", "υ");
        LATEX_SYMBOLS.put("phi", "ϕ");
        LATEX_SYMBOLS.put("varphi", "φ");
        LATEX_SYMBOLS.put("chi", "χ");
        LATEX_SYMBOLS.put("psi", "ψ");
        LATEX_SYMBOLS.put("omega", "ω");

        // Uppercase Greek letters
        LATEX_SYMBOLS.put("Gamma", "Γ");
        LATEX_SYMBOLS.put("Delta", "Δ");
        LATEX_SYMBOLS.put("Theta", "Θ");
        LATEX_SYMBOLS.put("Lambda", "Λ");
        LATEX_SYMBOLS.put("Xi", "Ξ");
        LATEX_SYMBOLS.put("Pi", "Π");
        LATEX_SYMBOLS.put("Sigma", "Σ");
        LATEX_SYMBOLS.put("Upsilon", "Υ");
        LATEX_SYMBOLS.put("Phi", "Φ");
        LATEX_SYMBOLS.put("Psi", "Ψ");
        LATEX_SYMBOLS.put("Omega", "Ω");

        // Relations
        LATEX_SYMBOLS.put("le", "≤");
        LATEX_SYMBOLS.put("leq", "≤");
        LATEX_SYMBOLS.put("ge", "≥");
        LATEX_SYMBOLS.put("geq", "≥");
        LATEX_SYMBOLS.put("neq", "≠");
        LATEX_SYMBOLS.put("ne", "≠");
        LATEX_SYMBOLS.put("approx", "≈");
        LATEX_SYMBOLS.put("sim", "∼");
        LATEX_SYMBOLS.put("simeq", "≃");
        LATEX_SYMBOLS.put("cong", "≅");
        LATEX_SYMBOLS.put("equiv", "≡");
        LATEX_SYMBOLS.put("propto", "∝");
        LATEX_SYMBOLS.put("ll", "≪");
        LATEX_SYMBOLS.put("gg", "≫");
        LATEX_SYMBOLS.put("prec", "≺");
        LATEX_SYMBOLS.put("succ", "≻");
        LATEX_SYMBOLS.put("preceq", "≼");
        LATEX_SYMBOLS.put("succeq", "≽");
        LATEX_SYMBOLS.put("parallel", "∥");
        LATEX_SYMBOLS.put("perp", "⊥");

        // Set theory & logic
        LATEX_SYMBOLS.put("in", "∈");
        LATEX_SYMBOLS.put("notin", "∉");
        LATEX_SYMBOLS.put("ni", "∋");
        LATEX_SYMBOLS.put("owns", "∋");
        LATEX_SYMBOLS.put("subset", "⊂");
        LATEX_SYMBOLS.put("subseteq", "⊆");
        LATEX_SYMBOLS.put("supset", "⊃");
        LATEX_SYMBOLS.put("supseteq", "⊇");
        LATEX_SYMBOLS.put("cap", "∩");
        LATEX_SYMBOLS.put("cup", "∪");
        LATEX_SYMBOLS.put("setminus", "∖");
        LATEX_SYMBOLS.put("emptyset", "∅");
        LATEX_SYMBOLS.put("varnothing", "∅");
        LATEX_SYMBOLS.put("forall", "∀");
        LATEX_SYMBOLS.put("exists", "∃");
        LATEX_SYMBOLS.put("nexists", "∄");
        LATEX_SYMBOLS.put("land", "∧");
        LATEX_SYMBOLS.put("wedge", "∧");
        LATEX_SYMBOLS.put("lor", "∨");
        LATEX_SYMBOLS.put("vee", "∨");
        LATEX_SYMBOLS.put("neg", "¬");
        LATEX_SYMBOLS.put("lnot", "¬");
        LATEX_SYMBOLS.put("top", "⊤");
        LATEX_SYMBOLS.put("bot", "⊥");

        // Binary operators & arithmetic
        LATEX_SYMBOLS.put("pm", "±");
        LATEX_SYMBOLS.put("mp", "∓");
        LATEX_SYMBOLS.put("times", "×");
        LATEX_SYMBOLS.put("div", "÷");
        LATEX_SYMBOLS.put("cdot", "·");
        LATEX_SYMBOLS.put("circ", "∘");
        LATEX_SYMBOLS.put("bullet", "•");
        LATEX_SYMBOLS.put("oplus", "⊕");
        LATEX_SYMBOLS.put("ominus", "⊖");
        LATEX_SYMBOLS.put("otimes", "⊗");
        LATEX_SYMBOLS.put("oslash", "⊘");
        LATEX_SYMBOLS.put("odot", "⊙");
        LATEX_SYMBOLS.put("star", "⋆");
        LATEX_SYMBOLS.put("ast", "∗");
        LATEX_SYMBOLS.put("dagger", "†");
        LATEX_SYMBOLS.put("ddagger", "‡");
        LATEX_SYMBOLS.put("bmod", "mod");
        LATEX_SYMBOLS.put("pmod", "mod");

        // Arrows
        LATEX_SYMBOLS.put("to", "→");
        LATEX_SYMBOLS.put("rightarrow", "→");
        LATEX_SYMBOLS.put("leftarrow", "←");
        LATEX_SYMBOLS.put("gets", "←");
        LATEX_SYMBOLS.put("leftrightarrow", "↔");
        LATEX_SYMBOLS.put("Rightarrow", "⇒");
        LATEX_SYMBOLS.put("implies", "⇒");
        LATEX_SYMBOLS.put("Leftarrow", "⇐");
        LATEX_SYMBOLS.put("Leftrightarrow", "⇔");
        LATEX_SYMBOLS.put("iff", "⇔");
        LATEX_SYMBOLS.put("mapsto", "↦");
        LATEX_SYMBOLS.put("uparrow", "↑");
        LATEX_SYMBOLS.put("downarrow", "↓");
        LATEX_SYMBOLS.put("nearrow", "↗");
        LATEX_SYMBOLS.put("searrow", "↘");
        LATEX_SYMBOLS.put("nwarrow", "↖");
        LATEX_SYMBOLS.put("swarrow", "↙");

        // Calculus, integrals, sums, misc
        LATEX_SYMBOLS.put("infty", "∞");
        LATEX_SYMBOLS.put("partial", "∂");
        LATEX_SYMBOLS.put("nabla", "∇");
        LATEX_SYMBOLS.put("sum", "∑");
        LATEX_SYMBOLS.put("prod", "∏");
        LATEX_SYMBOLS.put("coprod", "∐");
        LATEX_SYMBOLS.put("int", "∫");
        LATEX_SYMBOLS.put("iint", "∬");
        LATEX_SYMBOLS.put("iiint", "∭");
        LATEX_SYMBOLS.put("oint", "∮");
        LATEX_SYMBOLS.put("sqrt", "√");
        LATEX_SYMBOLS.put("angle", "∠");
        LATEX_SYMBOLS.put("deg", "°");
        LATEX_SYMBOLS.put("aleph", "ℵ");
        LATEX_SYMBOLS.put("ell", "ℓ");
        LATEX_SYMBOLS.put("hbar", "ℏ");
        LATEX_SYMBOLS.put("Re", "ℜ");
        LATEX_SYMBOLS.put("Im", "ℑ");
        LATEX_SYMBOLS.put("dots", "…");
        LATEX_SYMBOLS.put("cdots", "⋯");
        LATEX_SYMBOLS.put("ddots", "⋱");
        LATEX_SYMBOLS.put("vdots", "⋮");
        LATEX_SYMBOLS.put("ldots", "…");

        // Number sets
        LATEX_SYMBOLS.put("mathbb{R}", "ℝ");
        LATEX_SYMBOLS.put("mathbb{C}", "ℂ");
        LATEX_SYMBOLS.put("mathbb{N}", "ℕ");
        LATEX_SYMBOLS.put("mathbb{Z}", "ℤ");
        LATEX_SYMBOLS.put("mathbb{Q}", "ℚ");
        LATEX_SYMBOLS.put("mathbb{P}", "ℙ");
        LATEX_SYMBOLS.put("mathbb{H}", "ℍ");

        // Common math functions
        LATEX_SYMBOLS.put("sin", "sin");
        LATEX_SYMBOLS.put("cos", "cos");
        LATEX_SYMBOLS.put("tan", "tan");
        LATEX_SYMBOLS.put("log", "log");
        LATEX_SYMBOLS.put("ln", "ln");
        LATEX_SYMBOLS.put("exp", "exp");
        LATEX_SYMBOLS.put("lim", "lim");
        LATEX_SYMBOLS.put("max", "max");
        LATEX_SYMBOLS.put("min", "min");
        LATEX_SYMBOLS.put("sup", "sup");
        LATEX_SYMBOLS.put("inf", "inf");
        LATEX_SYMBOLS.put("gcd", "gcd");
        LATEX_SYMBOLS.put("det", "det");
        LATEX_SYMBOLS.put("dim", "dim");

        // Spacing
        LATEX_SYMBOLS.put("quad", "  ");
        LATEX_SYMBOLS.put("qquad", "    ");
        LATEX_SYMBOLS.put(",", " ");
        LATEX_SYMBOLS.put(";", " ");
        LATEX_SYMBOLS.put(":", " ");
        LATEX_SYMBOLS.put("!", "");

        // Superscripts
        SUPERSCRIPTS.put('0', '⁰');
        SUPERSCRIPTS.put('1', '¹');
        SUPERSCRIPTS.put('2', '²');
        SUPERSCRIPTS.put('3', '³');
        SUPERSCRIPTS.put('4', '⁴');
        SUPERSCRIPTS.put('5', '⁵');
        SUPERSCRIPTS.put('6', '⁶');
        SUPERSCRIPTS.put('7', '⁷');
        SUPERSCRIPTS.put('8', '⁸');
        SUPERSCRIPTS.put('9', '⁹');
        SUPERSCRIPTS.put('+', '⁺');
        SUPERSCRIPTS.put('-', '⁻');
        SUPERSCRIPTS.put('=', '⁼');
        SUPERSCRIPTS.put('(', '⁽');
        SUPERSCRIPTS.put(')', '⁾');
        SUPERSCRIPTS.put('n', 'ⁿ');
        SUPERSCRIPTS.put('i', 'ⁱ');
        SUPERSCRIPTS.put('j', 'ʲ');
        SUPERSCRIPTS.put('k', 'ᵏ');
        SUPERSCRIPTS.put('x', 'ˣ');
        SUPERSCRIPTS.put('y', 'ʸ');
        SUPERSCRIPTS.put('z', 'ᶻ');
        SUPERSCRIPTS.put('a', 'ᵃ');
        SUPERSCRIPTS.put('b', 'ᵇ');
        SUPERSCRIPTS.put('c', 'ᶜ');
        SUPERSCRIPTS.put('d', 'ᵈ');
        SUPERSCRIPTS.put('e', 'ᵉ');
        SUPERSCRIPTS.put('f', 'ᶠ');
        SUPERSCRIPTS.put('g', 'ᵍ');
        SUPERSCRIPTS.put('h', 'ʰ');
        SUPERSCRIPTS.put('m', 'ᵐ');
        SUPERSCRIPTS.put('p', 'ᵖ');
        SUPERSCRIPTS.put('r', 'ʳ');
        SUPERSCRIPTS.put('s', 'ˢ');
        SUPERSCRIPTS.put('t', 'ᵗ');
        SUPERSCRIPTS.put('u', 'ᵘ');
        SUPERSCRIPTS.put('v', 'ᵛ');
        SUPERSCRIPTS.put('w', 'ʷ');

        // Subscripts
        SUBSCRIPTS.put('0', '₀');
        SUBSCRIPTS.put('1', '₁');
        SUBSCRIPTS.put('2', '₂');
        SUBSCRIPTS.put('3', '₃');
        SUBSCRIPTS.put('4', '₄');
        SUBSCRIPTS.put('5', '₅');
        SUBSCRIPTS.put('6', '₆');
        SUBSCRIPTS.put('7', '₇');
        SUBSCRIPTS.put('8', '₈');
        SUBSCRIPTS.put('9', '₉');
        SUBSCRIPTS.put('+', '₊');
        SUBSCRIPTS.put('-', '₋');
        SUBSCRIPTS.put('=', '₌');
        SUBSCRIPTS.put('(', '₍');
        SUBSCRIPTS.put(')', '₎');
        SUBSCRIPTS.put('a', 'ₐ');
        SUBSCRIPTS.put('e', 'ₑ');
        SUBSCRIPTS.put('h', 'ₕ');
        SUBSCRIPTS.put('i', 'ᵢ');
        SUBSCRIPTS.put('j', 'ⱼ');
        SUBSCRIPTS.put('k', 'ₖ');
        SUBSCRIPTS.put('l', 'ₗ');
        SUBSCRIPTS.put('m', 'ₘ');
        SUBSCRIPTS.put('n', 'ₙ');
        SUBSCRIPTS.put('o', 'ₒ');
        SUBSCRIPTS.put('p', 'ₚ');
        SUBSCRIPTS.put('r', 'ᵣ');
        SUBSCRIPTS.put('s', 'ₛ');
        SUBSCRIPTS.put('t', 'ₜ');
        SUBSCRIPTS.put('u', 'ᵤ');
        SUBSCRIPTS.put('v', 'ᵥ');
        SUBSCRIPTS.put('x', 'ₓ');
    }

    private MathSymbolConverter() {}

    /**
     * Converts mathematical notations, MathJax, and LaTeX formulas in Markdown into clean Unicode.
     */
    public static String convertMathInMarkdown(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return markdown;
        }

        StringBuilder result = new StringBuilder();
        int length = markdown.length();
        int i = 0;

        while (i < length) {
            // 1. Preserve fenced code blocks: ```...```
            if (markdown.startsWith("```", i)) {
                int end = markdown.indexOf("```", i + 3);
                if (end != -1) {
                    result.append(markdown, i, end + 3);
                    i = end + 3;
                } else {
                    result.append(markdown.substring(i));
                    break;
                }
                continue;
            }

            // 2. Preserve inline code spans: `...`
            if (markdown.charAt(i) == '`') {
                int end = markdown.indexOf('`', i + 1);
                if (end != -1) {
                    result.append(markdown, i, end + 1);
                    i = end + 1;
                } else {
                    result.append(markdown.charAt(i));
                    i++;
                }
                continue;
            }

            // 3. Preserve markdown links: [text](url) - only convert math in link label
            if (markdown.charAt(i) == '[' && !markdown.startsWith("![", Math.max(0, i - 1))) {
                int closeBracket = findMatchingBracket(markdown, i, '[', ']');
                if (closeBracket != -1 && closeBracket + 1 < length && markdown.charAt(closeBracket + 1) == '(') {
                    int closeParen = findMatchingBracket(markdown, closeBracket + 1, '(', ')');
                    if (closeParen != -1) {
                        String linkText = markdown.substring(i + 1, closeBracket);
                        String linkUrl = markdown.substring(closeBracket + 2, closeParen);
                        result.append("[")
                                .append(convertMathExpression(linkText))
                                .append("](")
                                .append(linkUrl)
                                .append(")");
                        i = closeParen + 1;
                        continue;
                    }
                }
            }

            // 4. Block math: $$...$$
            if (markdown.startsWith("$$", i)) {
                int end = markdown.indexOf("$$", i + 2);
                if (end != -1) {
                    String mathBlock = markdown.substring(i + 2, end).trim();
                    result.append("\n\n")
                            .append(convertMathExpression(mathBlock))
                            .append("\n\n");
                    i = end + 2;
                    continue;
                }
            }

            // 5. MathJax block: \[...\] or \\[...\\]
            if (markdown.startsWith("\\[", i) || markdown.startsWith("\\\\[", i)) {
                int prefixLen = markdown.startsWith("\\\\[", i) ? 3 : 2;
                String closeDelim = prefixLen == 3 ? "\\\\]" : "\\]";
                int end = markdown.indexOf(closeDelim, i + prefixLen);
                if (end != -1) {
                    String mathBlock = markdown.substring(i + prefixLen, end).trim();
                    result.append("\n\n")
                            .append(convertMathExpression(mathBlock))
                            .append("\n\n");
                    i = end + closeDelim.length();
                    continue;
                }
            }

            // 6. MathJax inline: \(...\) or \\(...\\)
            if (markdown.startsWith("\\(", i) || markdown.startsWith("\\\\(", i)) {
                int prefixLen = markdown.startsWith("\\\\(", i) ? 3 : 2;
                String closeDelim = prefixLen == 3 ? "\\\\)" : "\\)";
                int end = markdown.indexOf(closeDelim, i + prefixLen);
                if (end != -1) {
                    String mathInline = markdown.substring(i + prefixLen, end).trim();
                    result.append(" ").append(convertMathExpression(mathInline)).append(" ");
                    i = end + closeDelim.length();
                    continue;
                }
            }

            // 7. Inline math: $...$
            if (markdown.charAt(i) == '$') {
                int end = findInlineDollarEnd(markdown, i);
                if (end != -1) {
                    String mathInline = markdown.substring(i + 1, end).trim();
                    result.append(" ").append(convertMathExpression(mathInline)).append(" ");
                    i = end + 1;
                    continue;
                }
            }

            // Regular character
            result.append(markdown.charAt(i));
            i++;
        }

        // Apply fallback conversions on raw LaTeX commands that weren't wrapped in delimiters
        String converted = convertStandaloneLatex(result.toString());

        return replaceCommonHtmlEntities(converted);
    }

    private static int findMatchingBracket(String s, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findInlineDollarEnd(String s, int start) {
        for (int j = start + 1; j < s.length(); j++) {
            char c = s.charAt(j);
            if (c == '\n') {
                return -1;
            }
            if (c == '$' && s.charAt(j - 1) != '\\') {
                return j;
            }
        }
        return -1;
    }

    /**
     * Converts a single LaTeX math expression into readable Unicode.
     */
    public static String convertMathExpression(String rawMath) {
        if (rawMath == null || rawMath.trim().isEmpty()) {
            return "";
        }

        String math = rawMath.trim();

        // 1. Remove outer math wrappers like \text{...}, \mathrm{...}, \mathbf{...}
        math = removeWrapperCommands(math);

        // 2. Fractions: \frac{a}{b} or \dfrac{a}{b} -> (a / b)
        math = convertFractions(math);

        // 3. Square roots: \sqrt{x} -> √(x), \sqrt[n]{x} -> ⁿ√(x)
        math = convertSquareRoots(math);

        // 4. LaTeX command replacements (\alpha -> α, \le -> ≤, etc.)
        math = replaceLatexCommands(math);

        // 5. Superscripts: x^2 -> x², x^{12} -> x¹², x^n -> xⁿ
        math = convertSuperscripts(math);

        // 6. Subscripts: x_i -> xᵢ, x_{12} -> x₁₂, x_0 -> x₀
        math = convertSubscripts(math);

        // 7. Cleanup remaining LaTeX delimiters and braces
        math = math.replace("\\{", "{")
                   .replace("\\}", "}")
                   .replace("\\left(", "(")
                   .replace("\\right)", ")")
                   .replace("\\left[", "[")
                   .replace("\\right]", "]")
                   .replace("\\left\\{", "{")
                   .replace("\\right\\}", "}")
                   .replace("\\left|", "|")
                   .replace("\\right|", "|")
                   .replace("\\left.", "")
                   .replace("\\right.", "")
                   .replace("\\,", " ")
                   .replace("\\;", " ")
                   .replace("\\:", " ")
                   .replace("\\!", "")
                   .replace("\\ ", " ")
                   .replaceAll("[{}]", "")
                   .replaceAll("\\s+", " ")
                   .trim();

        return math;
    }

    /**
     * Converts standalone LaTeX commands in raw text that were written without $ or \( delimiters.
     */
    private static String convertStandaloneLatex(String text) {
        if (text == null || !text.contains("\\")) {
            return text;
        }

        // Replace fractions: \frac{a}{b}
        text = convertFractions(text);

        // Replace roots: \sqrt{x}
        text = convertSquareRoots(text);

        // Replace commands: \alpha, \beta, \le, \ge, \sum, etc.
        text = replaceLatexCommands(text);

        // Replace superscripts / subscripts
        text = convertSuperscripts(text);
        text = convertSubscripts(text);

        return text;
    }

    private static String removeWrapperCommands(String text) {
        Pattern pattern = Pattern.compile("\\\\(?:text|mathrm|mathbf|mathit|boldsymbol|operatorname|mathbb|mathcal)\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(inner));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String convertFractions(String text) {
        Pattern fracPattern = Pattern.compile("\\\\(?:d)?frac\\{([^{}]+)\\}\\{([^{}]+)\\}");
        String current = text;
        Matcher m = fracPattern.matcher(current);
        while (m.find()) {
            String num = m.group(1).trim();
            String den = m.group(2).trim();
            String replacement = "(" + convertMathExpression(num) + " / " + convertMathExpression(den) + ")";
            current = current.substring(0, m.start()) + replacement + current.substring(m.end());
            m = fracPattern.matcher(current);
        }
        return current;
    }

    private static String convertSquareRoots(String text) {
        // \sqrt[n]{x}
        Pattern nRootPattern = Pattern.compile("\\\\sqrt\\[([^{}]+)\\]\\{([^{}]+)\\}");
        Matcher m1 = nRootPattern.matcher(text);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            String n = m1.group(1).trim();
            String x = m1.group(2).trim();
            String superN = toSuperscript(n);
            m1.appendReplacement(sb1, Matcher.quoteReplacement(superN + "√(" + convertMathExpression(x) + ")"));
        }
        m1.appendTail(sb1);
        String step1 = sb1.toString();

        // \sqrt{x}
        Pattern sqrtPattern = Pattern.compile("\\\\sqrt\\{([^{}]+)\\}");
        Matcher m2 = sqrtPattern.matcher(step1);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            String x = m2.group(1).trim();
            m2.appendReplacement(sb2, Matcher.quoteReplacement("√(" + convertMathExpression(x) + ")"));
        }
        m2.appendTail(sb2);
        return sb2.toString();
    }

    private static String replaceLatexCommands(String text) {
        Pattern cmdPattern = Pattern.compile("\\\\([a-zA-Z]+)");
        Matcher m = cmdPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String cmd = m.group(1);
            String sym = LATEX_SYMBOLS.get(cmd);
            if (sym != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(sym));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(cmd));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String convertSuperscripts(String text) {
        // x^{123}
        Pattern pBrace = Pattern.compile("\\^\\{([^{}]+)\\}");
        Matcher mBrace = pBrace.matcher(text);
        StringBuffer sbBrace = new StringBuffer();
        while (mBrace.find()) {
            String content = mBrace.group(1);
            String sup = toSuperscript(content);
            mBrace.appendReplacement(sbBrace, Matcher.quoteReplacement(sup));
        }
        mBrace.appendTail(sbBrace);

        // x^2 or x^n (single character)
        Pattern pSingle = Pattern.compile("(?<=[a-zA-Z0-9)])\\^([0-9a-zA-Z+-])");
        Matcher mSingle = pSingle.matcher(sbBrace.toString());
        StringBuffer sbSingle = new StringBuffer();
        while (mSingle.find()) {
            char c = mSingle.group(1).charAt(0);
            Character sup = SUPERSCRIPTS.get(c);
            if (sup != null) {
                mSingle.appendReplacement(sbSingle, Matcher.quoteReplacement(String.valueOf(sup)));
            } else {
                mSingle.appendReplacement(sbSingle, Matcher.quoteReplacement("^" + c));
            }
        }
        mSingle.appendTail(sbSingle);
        return sbSingle.toString();
    }

    private static String convertSubscripts(String text) {
        // x_{123}
        Pattern pBrace = Pattern.compile("_\\{([^{}]+)\\}");
        Matcher mBrace = pBrace.matcher(text);
        StringBuffer sbBrace = new StringBuffer();
        while (mBrace.find()) {
            String content = mBrace.group(1);
            String sub = toSubscript(content);
            mBrace.appendReplacement(sbBrace, Matcher.quoteReplacement(sub));
        }
        mBrace.appendTail(sbBrace);

        // x_i or x_0 (single character)
        Pattern pSingle = Pattern.compile("(?<=[a-zA-Z0-9)])_([0-9a-zA-Z+-])");
        Matcher mSingle = pSingle.matcher(sbBrace.toString());
        StringBuffer sbSingle = new StringBuffer();
        while (mSingle.find()) {
            char c = mSingle.group(1).charAt(0);
            Character sub = SUBSCRIPTS.get(c);
            if (sub != null) {
                mSingle.appendReplacement(sbSingle, Matcher.quoteReplacement(String.valueOf(sub)));
            } else {
                mSingle.appendReplacement(sbSingle, Matcher.quoteReplacement("_" + c));
            }
        }
        mSingle.appendTail(sbSingle);
        return sbSingle.toString();
    }

    private static String toSuperscript(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            Character sup = SUPERSCRIPTS.get(c);
            sb.append(sup != null ? sup : c);
        }
        return sb.toString();
    }

    private static String toSubscript(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            Character sub = SUBSCRIPTS.get(c);
            sb.append(sub != null ? sub : c);
        }
        return sb.toString();
    }

    private static String replaceCommonHtmlEntities(String text) {
        if (text == null || !text.contains("&")) {
            return text;
        }
        return text.replace("&le;", "≤")
                .replace("&ge;", "≥")
                .replace("&ne;", "≠")
                .replace("&times;", "×")
                .replace("&divide;", "÷")
                .replace("&plusmn;", "±")
                .replace("&infin;", "∞")
                .replace("&radic;", "√")
                .replace("&sum;", "∑")
                .replace("&prod;", "∏")
                .replace("&int;", "∫")
                .replace("&part;", "∂")
                .replace("&nabla;", "∇")
                .replace("&alpha;", "α")
                .replace("&beta;", "β")
                .replace("&gamma;", "γ")
                .replace("&delta;", "δ")
                .replace("&epsilon;", "ε")
                .replace("&theta;", "θ")
                .replace("&lambda;", "λ")
                .replace("&mu;", "μ")
                .replace("&pi;", "π")
                .replace("&sigma;", "σ")
                .replace("&tau;", "τ")
                .replace("&phi;", "ϕ")
                .replace("&omega;", "ω")
                .replace("&Delta;", "Δ")
                .replace("&Theta;", "Θ")
                .replace("&Lambda;", "Λ")
                .replace("&Sigma;", "Σ")
                .replace("&Phi;", "Φ")
                .replace("&Omega;", "Ω")
                .replace("&cup;", "∪")
                .replace("&cap;", "∩")
                .replace("&empty;", "∅")
                .replace("&isin;", "∈")
                .replace("&notin;", "∉")
                .replace("&sub;", "⊂")
                .replace("&sube;", "⊆")
                .replace("&supe;", "⊇")
                .replace("&and;", "∧")
                .replace("&or;", "∨")
                .replace("&not;", "¬")
                .replace("&rarr;", "→")
                .replace("&larr;", "←")
                .replace("&harr;", "↔")
                .replace("&rArr;", "⇒")
                .replace("&lArr;", "⇐")
                .replace("&hArr;", "⇔")
                .replace("&oplus;", "⊕")
                .replace("&perp;", "⊥")
                .replace("&cong;", "≅")
                .replace("&asymp;", "≈")
                .replace("&prop;", "∝")
                .replace("&prime;", "′")
                .replace("&Prime;", "″");
    }
}

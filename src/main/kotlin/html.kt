package com.util.html

//swiped from https://kotlinlang.org/docs/reference/type-safe-builders.html

interface Element {
    fun render(builder: StringBuilder, indent: String)
}

class TextElement(val text: String) : Element {
    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent$text\n")
    }
}

@DslMarker
annotation class HtmlTagMarker

@HtmlTagMarker
abstract class Tag(val name: String) : Element {
    val children = arrayListOf<Element>()
    val attributes = hashMapOf<String, String>()

    protected fun <T : Element> initTag(tag: T, init: T.() -> Unit): T {
        tag.init()
        children.add(tag)
        return tag
    }

    override fun render(builder: StringBuilder, indent: String) {
        builder.append("$indent<$name${renderAttributes()}>\n")
        for (c in children) {
            c.render(builder, indent + "  ")
        }
        builder.append("$indent</$name>\n")
    }

    private fun renderAttributes(): String {
        val builder = StringBuilder()
        for ((attr, value) in attributes) {
            builder.append(" $attr=\"$value\"")
        }
        return builder.toString()
    }

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }
}

abstract class TagWithText(name: String) : Tag(name) {
    operator fun String.unaryPlus() {
        children.add(TextElement(this))
    }
}

class HTML : TagWithText("html") {
    fun head(init: Head.() -> Unit) = initTag(Head(), init)

    fun body(init: Body.() -> Unit) = initTag(Body(), init)
}

class Head : TagWithText("head") {
    fun title(init: Title.() -> Unit) = initTag(Title(), init)
    fun style(init: STYLE.() -> Unit) = initTag(STYLE(), init)
}

class Title : TagWithText("title")

abstract class BodyTag(name: String) : TagWithText(name) {
    fun b(init: B.() -> Unit) = initTag(B(), init)
    fun p(init: P.() -> Unit) = initTag(P(), init)
    fun h1(init: H1.() -> Unit) = initTag(H1(), init)
    fun h2(init: H2.() -> Unit) = initTag(H2(), init)
    fun a(href: String, init: A.() -> Unit) {
        val a = initTag(A(), init)
        a.href = href
    }
    fun svg(width: String, height: String, init: SVG.() -> Unit) {
        val svg = initTag(SVG(), init)
        svg.width = width
        svg.height = height
    }

    fun circle(cx: String, cy: String, r: String, fill: String, init: CIRCLE.() -> Unit) {
        val circle = initTag(CIRCLE(), init)
        circle.cx = cx
        circle.cy = cy
        circle.r = r
        circle.fill = fill
    }

    fun rect(width: String, height: String, style: String = "", init: RECT.() -> Unit) {
        val rect = initTag(RECT(), init)
        rect.width = width
        rect.height = height
        rect.style = style
    }

    fun line(x1: String, y1: String, x2: String, y2: String, style: String = "", init: LINE.() -> Unit) {
        val line = initTag(LINE(), init)
        line.x1 = x1
        line.y1 = y1
        line.x2 = x2
        line.y2 = y2
        line.style = style
    }

    fun text(fill: String, fontsize: String, fontfamily: String, x: String, y: String, init: TEXT.() -> Unit) {
        val text =initTag(TEXT(), init)
        text.fill = fill
        text.fontsize = fontsize
        text.fontfamily = fontfamily
        text.x = x
        text.y = y
    }

    fun table(style: String, init: TABLE.() -> Unit) {
        val table = initTag(TABLE(), init)
        table.style = style
    }
    fun tr(init: TR.() -> Unit) = initTag(TR(), init)
    fun td(init: TD.() -> Unit) = initTag(TD(), init)
}

class Body : BodyTag("body")
class B : BodyTag("b")
class P : BodyTag("p")
class H1 : BodyTag("h1")
class H2 : BodyTag("h2")
class TABLE : BodyTag("table") {
    var style: String
        get() = attributes["style"]!!
        set(value) {
            attributes["style"] = value
        }
}
class TR : BodyTag("tr")
class TD : BodyTag("td")
class STYLE : BodyTag("style")

class SVG : BodyTag("svg") {
    var width: String
        get() = attributes["width"]!!
        set(value) {
            attributes["width"] = value
        }
    var height: String
        get() = attributes["height"]!!
        set(value) {
            attributes["height"] = value
        }
}

class CIRCLE : BodyTag("circle") {
    var cx:  String
        get() = attributes["cx"]!!
        set(value) {
            attributes["cx"] = value
        }
    var cy:  String
        get() = attributes["cy"]!!
        set(value) {
            attributes["cy"] = value
        }
    var r:  String
        get() = attributes["r"]!!
        set(value) {
            attributes["r"] = value
        }
    var fill:  String
        get() = attributes["fill"]!!
        set(value) {
            attributes["fill"] = value
        }
}

class RECT : BodyTag("rect") {
    var width: String
        get() = attributes["width"]!!
        set(value) {
            attributes["width"] = value
        }
    var height: String
        get() = attributes["height"]!!
        set(value) {
            attributes["height"] = value
        }
    var style:  String
        get() = attributes["style"]!!
        set(value) {
            attributes["style"] = value
        }
}

class LINE : BodyTag("line") {
    var x1: String
        get() = attributes["x1"]!!
        set(value) {
            attributes["x1"] = value
        }
    var y1: String
        get() = attributes["y1"]!!
        set(value) {
            attributes["y1"] = value
        }
    var x2: String
        get() = attributes["x2"]!!
        set(value) {
            attributes["x2"] = value
        }
    var y2: String
        get() = attributes["y2"]!!
        set(value) {
            attributes["y2"] = value
        }
    var style:  String
        get() = attributes["style"]!!
        set(value) {
            attributes["style"] = value
        }
}

class TEXT : BodyTag("text") {
    var fill: String
        get() = attributes["fill"]!!
        set(value) {
            attributes["fill"] = value
        }
    var fontsize: String
        get() = attributes["font-size"]!!
        set(value) {
            attributes["font-size"] = value
        }
    var fontfamily: String
        get() = attributes["font-family"]!!
        set(value) {
            attributes["font-family"] = value
        }
    var x: String
        get() = attributes["x"]!!
        set(value) {
            attributes["x"] = value
        }
    var y: String
        get() = attributes["y"]!!
        set(value) {
            attributes["y"] = value
        }
}

class A : BodyTag("a") {
    var href: String
        get() = attributes["href"]!!
        set(value) {
            attributes["href"] = value
        }
}

fun html(init: HTML.() -> Unit): HTML {
    val html = HTML()
    html.init()
    return html
}


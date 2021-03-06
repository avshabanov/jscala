package org.jscalaexample

import org.scalatest.FunSuite
import org.jscala._
import org.jscala.{javascript=>js}
import scala.collection.mutable.ArrayBuffer

class ScalaToJsConverterTest extends FunSuite {
  test("Literals") {
    assert(js(1) === JsNum(1.0, isFloat = false))
    assert(js(-5.1) === JsNum(-5.1, isFloat = true))
    assert(js('c') === JsString("c"))
    assert(js("str") === JsString("str"))
    assert(js(true) === JsBool(true))
    assert(js(()) === JsUnit)
  }

  test("Unary operators") {
    val a = 0
    val b = false
    assert(js(+a) === JsUnOp("+", JsIdent("a")))
    assert(js(-a) === JsUnOp("-", JsIdent("a")))
    assert(js(!b) === JsUnOp("!", JsIdent("b")))
  }

  test("Binary operators") {
    val a = 0
    val b = 1
    val c = true
    val d = false
    var s = 0
    val ja = JsIdent("a")
    val jb = JsIdent("b")
    val jc = JsIdent("c")
    val jd = JsIdent("d")
    assert(js(a * b) === JsBinOp("*", ja, jb))
    assert(js(a / b) === JsBinOp("/", ja, jb))
    assert(js(a % b) === JsBinOp("%", ja, jb))
    assert(js(a + b) === JsBinOp("+", ja, jb))
    assert(js(a - b) === JsBinOp("-", ja, jb))
    assert(js(a << b) === JsBinOp("<<", ja, jb))
    assert(js(a >> b) === JsBinOp(">>", ja, jb))
    assert(js(a >>> b) === JsBinOp(">>>", ja, jb))
    assert(js(a < b) === JsBinOp("<", ja, jb))
    assert(js(a > b) === JsBinOp(">", ja, jb))
    assert(js(a <= b) === JsBinOp("<=", ja, jb))
    assert(js(a >= b) === JsBinOp(">=", ja, jb))
    assert(js(a == b) === JsBinOp("==", ja, jb))
    assert(js(a != b) === JsBinOp("!=", ja, jb))
    assert(js(a & b) === JsBinOp("&", ja, jb))
    assert(js(a | b) === JsBinOp("|", ja, jb))
    assert(js(c && d) === JsBinOp("&&", jc, jd))
    assert(js(c || d) === JsBinOp("||", jc, jd))
    assert(js{s = a + b} === JsBinOp("=", JsIdent("s"), JsBinOp("+", ja, jb)))
    val lhs = JsBinOp("!=", JsBinOp("/", JsBinOp("+", ja, JsBinOp("*", jb, ja)), ja), jb)
    val expected = JsBinOp("&&", lhs, JsBinOp("==", jc, jb))
    assert(js(((a + b * a) / a != b) && c == b) === expected)
  }

  test("Simple expressions") {
    val local = 1
    def func1() = 1
    val obj = new {
      val field = 1
      def func2(i: Int) = "string"
    }
    def func3(f: Int => String) = f(1)
    assert(js(local) === JsIdent("local"))
    assert(js(func1()) === JsCall(JsIdent("func1"), Nil))
    val ast = js {
      val obj = new {
        val field = 1
        def func1(i: Int) = field
        def func2(i: Int) = "string"
      }
    }
    val fields = List("field" -> JsNum(1.0, false), "func1" -> JsAnonFunDecl(List("i"), JsBlock(List(JsReturn(JsSelect(JsIdent("this"), "field"))))), "func2" -> JsAnonFunDecl(List("i"), JsBlock(List(JsReturn(JsString("string"))))))
    assert(ast === JsBlock(List(JsVarDef("obj", JsAnonObjDecl(fields)))))
    assert(js(obj.func2(1)) === JsCall(JsSelect(JsIdent("obj"), "func2"), List(JsNum(1, false))))
    val lambda = JsAnonFunDecl(List("i"), JsBlock(List(JsReturn(JsCall(JsSelect(JsIdent("i"), "toString"), Nil)))))
    assert(js(func3(i => i.toString)) === JsCall(JsIdent("func3"), List(lambda)))

    val ifExprAst = js {
      var a = if (inject(local) > 1) 2 else 3
      val b = if (inject(local) > 1) {
        a += 1
        a
      } else {
        a += 2
        a
      }
      a + b
    }
    assert(ifExprAst.eval() === 10)
    val matchExprAst = js {
      val a = inject(local) match {
        case 1 | 2 => "one or two"
        case 3 => "three"
        case _ => "other"
      }
      a
    }
    assert(matchExprAst.eval() === "one or two")
  }

  test("Simple statements") {
    var a = 0
    val b = 1
    val c = true
    val d = false
    var s = 0
    val ja = JsIdent("a")
    val jb = JsIdent("b")
    val jc = JsIdent("c")
    val jd = JsIdent("d")
    val jss = JsIdent("s")
    assert(js { val a = 0 } === JsBlock(List(JsVarDef("a", JsNum(0, false)))))
    assert(js { val a = b + 2 } === JsBlock(List(JsVarDef("a", JsBinOp("+", JsIdent("b"), JsNum(2, false))))))
    assert(js { if (a > 0) b } === JsIf(JsBinOp(">", ja, JsNum(0, false)), JsExprStmt(jb), None))
    assert(js { if (a > 0) b else a } === JsIf(JsBinOp(">", ja, JsNum(0, false)), JsExprStmt(jb), Some(JsExprStmt(ja))))
    assert(js { while (a > 0) b } === JsWhile(JsBinOp(">", ja, JsNum(0, false)), JsExprStmt(jb)))
    val body = JsBlock(List(JsExprStmt(JsBinOp("=", jss, JsBinOp("+", jss, ja))), JsExprStmt(JsBinOp("=", ja, JsBinOp("+", ja, JsNum(1.0, false))))))
    val expected = JsWhile(JsBinOp(">", ja, JsNum(0, false)), body)
    assert(js {
      while (a > 0) {
        s = s + a
        a = a + 1
      } } === expected)

    assert(js { def func1() {} } === JsBlock(List(JsFunDecl("func1", Nil, JsBlock(Nil)))))
    assert(js { def func2 = 5 } === JsBlock(List(JsFunDecl("func2", Nil, JsBlock(List(JsReturn(JsNum(5.0,false))))))))
    assert(js { def func3() = 5 } === JsBlock(List(JsFunDecl("func3", Nil, JsBlock(List(JsReturn(JsNum(5.0,false))))))))
    assert(js { def func4(a: String) = 5 } === JsBlock(List(JsFunDecl("func4", List("a"), JsBlock(List(JsReturn(JsNum(5.0,false))))))))
    val ifElse = Some(JsBlock(List(JsVarDef("c", JsBinOp("*", jb, JsNum(2.0, true))), JsExprStmt(JsUnOp("-", jc)))))
    val jsIf = JsIf(JsBinOp(">", ja, JsNum(2.0, false)), JsExprStmt(JsBinOp("*", JsBinOp("+", ja, jb), JsNum(2.0, false))), ifElse)
    val bodyfunc5 = JsBlock(List(JsVarDef("b", JsNum(5.0, true)), jsIf))
    val expectedFunc5 = JsBlock(List(JsFunDecl("func5", List("a"), bodyfunc5)))
    assert(js {
      def func5(a: Int) = {
        val b = 5.0
        if (a > 2)
          (a + b) * 2
        else {
          val c = b * 2.0
          -c
        }
      }
    } === expectedFunc5)

    val els = Some(JsExprStmt(JsCall(JsSelect(JsIdent("console"), "log"), List(JsString("a")))))
    val func6Body = JsBlock(List(JsIf(JsBinOp(">", JsCall(JsSelect(ja, "length"), Nil), JsNum(2.0, false)), JsReturn(JsUnit), els)))
    assert(js { def func6(a: String) {
      if (a.length > 2) return else {
        console.log("a")
      }
    } } === JsBlock(List(JsFunDecl("func6",List("a"),func6Body))))
    val stmt = JsExprStmt(JsCall(JsSelect(JsIdent("console"), "log"), List(JsAccess(JsIdent("a"), JsIdent("i")))))
    val jsFor = JsFor(JsIdent("i"), JsNum(0.0, false), JsSelect(JsIdent("a"), "length"), stmt)
    assert(js {
      val a = Array(1, 2)
      for (i <- 0 until a.length) {
        console.log(a(i))
      }
    } === JsBlock(List(JsVarDef("a", JsArray(List(JsNum(1.0, false), JsNum(2.0, false)))), jsFor)))
  }

  test("String operations") {
    val ast = js {
      val a = new JString("str")
      a.replace("s", "a").slice(1, 2)
    }
    val call = JsCall(JsSelect(JsCall(JsSelect(JsIdent("a"), "replace"), List(JsString("s"), JsString("a"))), "slice"), List(JsNum(1.0, false), JsNum(2.0, false)))
    assert(ast === JsBlock(List(JsVarDef("a",JsString("str")), JsExprStmt(call))))
  }

  test("Arrays") {
    assert(js(Array(1, 2)) === JsArray(List(JsNum(1.0, false), JsNum(2.0, false))))
    assert(js(Array("1", "2")) === JsArray(List(JsString("1"), JsString("2"))))
    val call = JsCall(JsSelect(JsIdent("console"), "log"), List(JsIdent("i")))
    assert(js{
      val a = Array("1", "2")
      for (i <- a) console.log(i)
    } === JsBlock(List(JsVarDef("a", JsArray(List(JsString("1"), JsString("2")))), JsForIn(JsIdent("a"), JsIdent("i"), JsExprStmt(call)))))
    val call1 = JsCall(JsSelect(JsIdent("a"), "pop"), Nil)
    assert(js {
      val a = JArray("1", "2")
      a.pop()
    } === JsBlock(List(JsVarDef("a", JsArray(List(JsString("1"), JsString("2")))), JsExprStmt(call1))))
    val assign = JsExprStmt(JsBinOp("=",JsAccess(JsIdent("a"),JsNum(0.0,false)),JsAccess(JsIdent("a"),JsNum(1.0,false))))
    assert(js {
      val a = JArray(1, 2)
      a(0) = a(1)
      a.pop()
    } === JsBlock(List(JsVarDef("a", JsArray(List(JsNum(1.0,false), JsNum(2.0,false)))), assign, JsExprStmt(call1))))
    val ast = js {
      val a = Seq("1", "2")
      val b = ArrayBuffer(1, 2)
      parseInt(a(0)) + b(1)
    }
    assert(ast.eval() === 3)
  }

  test("Math") {
    val ast = js(Math.abs(-1))
    assert(ast === JsCall(JsSelect(JsIdent("Math"), "abs"), List(JsNum(-1.0, true))))
  }

  test("Global functions") {
    val ast = js(escape("asdf"))
    assert(ast === JsCall(JsIdent("escape"), List(JsString("asdf"))))
    val ast1 = js(typeof("asdf"))
    assert(ast1 === JsCall(JsIdent("typeof"), List(JsString("asdf"))))
    val ast2 = js {
      val a = include("[1, 2]").asInstanceOf[JArray[Int]]
      if (a.length > 1) {
        include("console.log(a[1])")
      }
    }
    val jsIf = JsIf(JsBinOp(">", JsSelect(JsIdent("a"), "length"), JsNum(1.0, false)), JsExprStmt(JsRaw("console.log(a[1])")), None)
    assert(ast2 === JsBlock(List(JsVarDef("a", JsRaw("[1, 2]")), jsIf)))
  }

  test("RegExp") {
    val ast = js {
      val a = new RegExp("d.*", "g")
      a.exec("asdf").toString()
    }
    val call = JsCall(JsSelect(JsCall(JsSelect(JsIdent("a"), "exec"), List(JsString("asdf"))), "toString"), Nil)
    assert(ast === JsBlock(List(JsVarDef("a",JsNew(JsCall(JsIdent("RegExp"),List(JsString("d.*"), JsString("g"))))), JsExprStmt(call))))
  }

  test("Date") {
    val ast = js {
      val a = new Date()
      a.getDay().toString()
    }
    val call = JsCall(JsSelect(JsCall(JsSelect(JsIdent("a"), "getDay"), Nil), "toString"), Nil)
    assert(ast === JsBlock(List(JsVarDef("a", JsNew(JsCall(JsIdent("Date"), Nil))), JsExprStmt(call))))
  }

  test("Maps") {
    import collection.mutable
    // Immutable Map
    val ast = js {
      val a = Map("field" -> JArray(1, 2), ("field2", JArray(1)), "field3" → JArray[Int]())
      a("field").pop().toString()
    }
    val call1 = JsCall(JsSelect(JsCall(JsSelect(JsAccess(JsIdent("a"), JsString("field")), "pop"), Nil), "toString"), Nil)
    val fields = List("field" -> JsArray(List(JsNum(1.0, false), JsNum(2.0, false))), "field2" -> JsArray(List(JsNum(1.0, false))), "field3" -> JsArray(Nil))
    assert(ast === JsBlock(List(JsVarDef("a", JsAnonObjDecl(fields)), JsExprStmt(call1))))

    // Mutable Map
    val ast1 = js {
      val a = mutable.Map("field" -> JArray(1, 2), ("field2", JArray(1)), "field3" → JArray[Int]())
      a("field") = a("field2")
    }
    val stmt = JsExprStmt(JsBinOp("=", JsAccess(JsIdent("a"), JsString("field")), JsAccess(JsIdent("a"), JsString("field2"))))
    assert(ast1 === JsBlock(List(JsVarDef("a", JsAnonObjDecl(fields)), stmt)))
  }

  test("Switch declaration") {
    val ast = javascript {
      val a: Any = "2"
      a match {
        case 1 | 2 => "1"
        case "2" => "2"
        case true => "true"
        case _ => "3"
      }
    }
    assert(ast.eval() === "2")
  }

  test("Try/catch/finally") {
    val ast = javascript {
      try { 1 } catch {
        case  e: Exception => 2
      }
    }
    assert(ast.eval() === 1.0)
    val ast1 = javascript { try { 1 } finally { 2 } }
    assert(ast1.eval() === 2.0)
    val ast2 = javascript {
      try { 1 } catch {
        case  e: Exception => 2
      } finally { 3 }
    }
    assert(ast2.eval() === 3.0)
  }

  test("Object declaration") {
    val ast = javascript {
      class A(val arg1: String, var arg2: Int, arg3: Array[String]) {
        val field = 1
        private var count = arg2
        def func1(i: Int) = field
        def func2(i: Int) = arg3(i)
        def func3() = arg3
        def func4() = func3()(0)
      }
      val a = new A("a", 1, Array("111", "222"))
      a.arg1 + a.func2(a.arg2) + a.field + a.func4()
    }
    assert(ast.eval() === "a2221111")
  }

  test("Lazy") {
    val x = 15
    val y = "hehe"
    def f() = "1"
    case class My(a: String)
    implicit def zzz: JsSerializer[My] = new JsSerializer[My] { def apply(a: My) = JsString(a.a) }
    val z = My("my")
    val ls = Seq(1, 2, 3).map(_.toJs)
    def genJsAst() = JsString("gen")
    val ast = javascript {
      val a = inject(x)
      val b = inject(y)
      val c = inject(z)
      val d = inject(f _)
      val e = inject(ls)
      val gen = genJsAst()
      a.as[String] + b + c + d + e.toString() + gen
    }
    assert(ast.eval() === "15hehemy11,2,3gen")
  }

  test("Implicit conversions") {
    val ast = javascript {
      val a: JString = "asdf"
      val b: JArray[Int] = Array(1, 2)
      val c: JArray[String] = ArrayBuffer("1", "2")
      def f(s: String, b: Array[Int], c: Array[String]) = s
      f(a, b, c)
    }
    assert(ast.eval() === "asdf")
  }

  test("JsDynamic") {
    val $ = new JsDynamic {}
    class XmlHttpRequest(s: String) extends JsDynamic
    val ast = javascript {
      val a = new XmlHttpRequest("request")
      a.foo("reply")
      $("button", this).click(() => $("p").css("color", "red"))
      $.field = $.select
    }
    val request = JsVarDef("a",JsNew(JsCall(JsIdent("XmlHttpRequest"),List(JsString("request")))))
    val foo = JsExprStmt(JsCall(JsSelect(JsIdent("a"),"foo"),List(JsString("reply"))))
    val returnExpr = JsCall(JsSelect(JsCall(JsSelect(JsIdent("$"), "apply"), List(JsString("p"))), "css"), List(JsString("color"), JsString("red")))
    val anonFunDecl = JsAnonFunDecl(List(), JsBlock(List(JsReturn(returnExpr))))
    val call = JsCall(JsSelect(JsCall(JsSelect(JsIdent("$"), "apply"), List(JsString("button"), JsIdent("this"))), "click"), List(anonFunDecl))
    val update = JsBinOp("=", JsSelect(JsIdent("$"), "field"), JsSelect(JsIdent("$"), "select"))
    assert(ast === JsBlock(List(request, foo, JsExprStmt(call), JsExprStmt(update))))
  }
}

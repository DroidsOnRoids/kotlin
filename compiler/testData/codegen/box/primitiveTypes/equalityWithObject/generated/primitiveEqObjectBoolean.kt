// Auto-generated by GeneratePrimitiveVsObjectEqualityTestData. Do not edit!

val nx: Any? = true
val nn: Any? = null
val x: Boolean = true
val y: Boolean = false

fun box(): String {
    val ax: Any? = true
    val an: Any? = null
    val bx: Boolean = true
    val by: Boolean = false

    return when {
        true != nx -> "Fail 0"
        false == nx -> "Fail 1"
        !(true == nx) -> "Fail 2"
        !(false != nx) -> "Fail 3"
        x != nx -> "Fail 4"
        y == nx -> "Fail 5"
        !(x == nx) -> "Fail 6"
        !(y != nx) -> "Fail 7"
        true == nn -> "Fail 8"
        !(true != nn) -> "Fail 9"
        x == nn -> "Fail 10"
        !(x != nn) -> "Fail 11"
        true != ax -> "Fail 12"
        false == ax -> "Fail 13"
        !(true == ax) -> "Fail 14"
        !(false != ax) -> "Fail 15"
        x != ax -> "Fail 16"
        y == ax -> "Fail 17"
        !(x == ax) -> "Fail 18"
        !(y != ax) -> "Fail 19"
        bx != ax -> "Fail 20"
        by == ax -> "Fail 21"
        !(bx == ax) -> "Fail 22"
        !(by != ax) -> "Fail 23"
        true == an -> "Fail 24"
        !(true != an) -> "Fail 25"
        x == an -> "Fail 26"
        !(x != an) -> "Fail 27"
        bx == an -> "Fail 28"
        !(bx != an) -> "Fail 29"
        else -> "OK"
    }
}

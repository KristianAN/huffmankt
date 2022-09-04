import java.io.File
import java.util.*
import kotlin.Comparator
import kotlin.math.max

/**
 * @author Kristian Nedrevold
 * Simple implementation of huffman coding written in pure kotlin using stdlib
 */

// Just to run our examples
fun main() {
    val input = Input(File("/home/kristian/IdeaProjects/huffmankt/resources/medium-input.huff").readText())
    //val input = Input("BCAADDDCCACACAC")
    val start = System.currentTimeMillis()
    val encoded = input.encode()
    println("Time spent encoding: ${System.currentTimeMillis() - start}")
    val tree = input.createHuffmanFromInput()
    val decoded = EncodedData(encoded.getOrNull()!!, tree).decode()
    println(decoded == input.input)
}

/**
 * [Node] The node(s) of a huffman tree
 */
data class Node(
    val content: Char?,
    val value: Int,
    val left: Node? = null,
    val right: Node? = null
) {
    fun height(): Result<Int> = heightFromRoot(this)

    // This is a bit messy when dealing with recursive calls to nullables
    private fun heightFromRoot(root: Node): Result<Int> {
        return if (root.leftIsNull() && root.rightIsNull()) Result.success(0) else {

            // This looks a bit strange, but it uses the builtin check for null with elvis operator and the Result Monad
            // It ends up being a decently elegant call
            val leftHeight =  root.left?.let { heightFromRoot(it) }
                ?.getOrElse { return Result.failure(it) } ?: return Result.failure(TreeHeightException)

            val rightHeight = root.right?.let {heightFromRoot(it) }
                ?.getOrElse { return Result.failure(it) } ?: return Result.failure(TreeHeightException)


            return Result.success(max(leftHeight, rightHeight) + 1)
        }
    }

    fun leftIsNull(): Boolean = left == null
    fun rightIsNull(): Boolean = right == null
}

/**
 * [EncodedData] Is a data carrier for the data we need to decompress/
 */
data class EncodedData(
    val bits: List<Boolean>,
    val huffman: Node,
) {
    fun decode(): String {
        var root = this.huffman

        return bits.mapNotNull { bool ->
            if (bool) {
                root.left?.let { root = it }
            } else {
                root.right?.let { root = it }
            }

            root.content?.let {
                root = huffman
                it
            }
        }.joinToString("")
    }
}

/**
 * [Input] Our input is just a String, but we can create some nice encapsulation by using
 * inline classes and extension functions. Our input now has a type and associated methods
 * while keeping all the runtime optimization that primitive types get!
 */
@JvmInline
value class Input(val input: String)

fun Input.createHuffmanFromInput(): Node {

    /*
     * If we want to use the PriorityQueue from the standard library we have to provide
     * our own comparator for our Types. This is easily implemented in kotlin without much boilerplate
     */
    val compareNodeByValue: Comparator<Node> = compareBy { it.value }

    // Kotlin allows us to have internal functions for private encapsulation
    fun getFrqPq(): PriorityQueue<Node> {
        val pq = PriorityQueue(compareNodeByValue)

        input.groupingBy { it }.eachCount().onEach { entry ->
            pq.add(Node(entry.key, entry.value))
        }

        return pq
    }

    val frq = getFrqPq()

    // Tailrec is a compiler optimization that ensures this is compiled into a while loop in the JVM bytecode
    tailrec fun createHuffmanFromPq (pq: PriorityQueue<Node>): Node {
        if (pq.size == 1) return pq.poll()

        val firstNode = pq.poll()
        val secondNode = pq.poll()

        val newValue = firstNode.value + secondNode.value
        pq.add(Node(
            null,
            newValue,
            firstNode,
            secondNode
        ))

        return createHuffmanFromPq(pq)
    }

    return createHuffmanFromPq(frq)
}

// Create a BooleanArray encoding of the input
fun Input.encode(): Result<List<Boolean>> {
    val root = createHuffmanFromInput()
    val codes: MutableMap<Char, List<Boolean>> = mutableMapOf()

    val codeList: MutableList<Boolean> = MutableList(root.height().getOrElse {
        return Result.failure(it)
    }) { true }

    val codesForChars = createCodes(root, codeList, codes, 0)


    kotlin.runCatching {
        return Result.success(input.map {char ->
            codesForChars[char]!!
        }.flatten())
    }.getOrElse {
        return Result.failure(it)
    }

}

private fun createCodes(root: Node, binCodes: MutableList<Boolean>, huffman: MutableMap<Char, List<Boolean>>, index: Int): MutableMap<Char, List<Boolean>> {

    if (root.leftIsNull() && root.rightIsNull()) {
        root.content?.let {
            huffman[it] = binCodes.subList(0, index).toList() // Careful to copy here, or we will pass in the reference to the original list, which is mutating.
        }
    }

    root.left?.let {
        binCodes[index] = true
        createCodes(it, binCodes , huffman, index + 1)
    }

    root.right?.let {
        binCodes[index] = false
        createCodes(it, binCodes, huffman,  index + 1)
    }

    return huffman
}

object TreeHeightException: Exception("Could not calculate tree height due to null leaf")

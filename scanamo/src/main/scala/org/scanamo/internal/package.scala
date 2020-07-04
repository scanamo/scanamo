package org.scanamo

/**
  * A purely functional implementation of the Calkin-Wilf tree, [[https://en.wikipedia.org/wiki/Calkin–Wilf_tree]].
  *
  * Out of the many properties of the tree, we are only interested in these:
  *
  * - the domain (ℝ) is infinite
  * - each vertex represents a unique element in the domain
  * - generating an element of the domain is a constant time operation
  *
  * Altogether, those properties are used to generate very cheap unique identifiers for
  * tree-like expressions (e.g. dynamodb condition expressions).
  *
  * Inspired by functional peal "Enumerating the Rationals" by Gibbons, Lester and Bird.
  * http://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/rationals.pdf
  */

package object internal {
  type CW = (Int, Int)

  implicit final private[scanamo] class CWOps(private val node: CW) extends AnyVal {
    def asKey: String =
      s"${node._1}_${node._2}"

    def split: (CW, CW) = {
      val m = node._1 + node._2
      (node._1 -> m) -> (m -> node._2)
    }
  }

  val CW: CW = 1 -> 1
}

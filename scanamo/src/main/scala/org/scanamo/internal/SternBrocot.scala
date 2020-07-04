package org.scanamo.internal

/**
  * A purely functional implementation of the Stern-Brocot tree, [[https://en.wikipedia.org/wiki/Stern–Brocot_tree]].
  *
  * Out of the many properties of the tree, we are only interested in these:
  *
  * - the domain (ℝ) is infinite
  * - any pruning of the tree has an increasing inorder traversal
  * - generating an element of the domain is a constant time operation
  *
  * Altogether, those properties are used to generate very cheap unique identifiers for
  * tree-like expressions (e.g. dynamodb condition expressions).
  *
  * Inspired by functional peal "Enumerating the Rationals" by Gibbons, Lester and Bird.
  * http://www.cs.ox.ac.uk/people/jeremy.gibbons/publications/rationals.pdf
  */
private case class SBNode private (m: Int, n: Int) {
  def mediant(that: SBNode): SBNode =
    SBNode(m + that.m, n + that.n)
}

private object SBNode {
  val `-∞` : SBNode = SBNode(0, 1)
  val `+∞` : SBNode = SBNode(1, 0)

  implicit def nodeToTuple(node: SBNode): (Int, Int) =
    node.m -> node.n
}

private[scanamo] class SB private (left: SBNode, right: SBNode) {

  def value: (Int, Int) = left mediant right

  def asKey: String = {
    val x = value
    s"${x._1}_${x._2}"
  }

  def split: (SB, SB) = {
    val m = left mediant right
    new SB(left, m) -> new SB(m, right)
  }

}

private[scanamo] object SB {
  val root: SB = new SB(SBNode.`-∞`, SBNode.`+∞`)
}

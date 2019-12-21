package com.brycegao.algorithm;

/**
 * 判断二叉树是否对称的
 *
 * 二叉树大都是用递归方式实现
 */
public class Leetcode101SymmetricTree {
  private class TreeNode {
      int val;
      TreeNode left;
      TreeNode right;
      TreeNode(int x) { val = x; }
  }

  public boolean isSymmetric(TreeNode root) {
    /**
     * 解题思路：
     * 1、根节点相同
     * 2、左子树和右子树是对称的
     **/
    if (root == null) {
      return true;
    }

    return helperCheck(root.left, root.right);
  }

  /**
   * 1、判断2个节点是否相同
   * 2、左节点的左子树和右节点的右子树；  左节点的右子树和右节点的左子树
   **/
  private boolean helperCheck(TreeNode leftNode, TreeNode rightNode) {
    if (leftNode == null && rightNode == null) {
      return true;
    } else if (leftNode != null && rightNode == null) {
      return false;
    } else if (leftNode == null && rightNode != null) {
      return false;
    } else if (leftNode.val != rightNode.val) {
      return false;
    }

    return helperCheck(leftNode.left, rightNode.right) && helperCheck(leftNode.right, rightNode.left);
  }
}

package com.brycegao.algorithm;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 二叉树层级遍历
 */
public class LeetCode102BinaryTree {
  private class TreeNode {
      int val;
      TreeNode left;
      TreeNode right;
     TreeNode(int x) { val = x; }
  }

  /**
   * 解题思路：层级遍历时使用队列实现， 记录每层的个数
   **/
  public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> listResult = new ArrayList();
    if (root == null) {
      return listResult;
    }
    Queue<TreeNode> queue = new LinkedList();
    queue.offer(root);
    while (!queue.isEmpty()) {
      int levelSize = queue.size();  //当前层级的节点个数
      List<Integer> listLevel = new ArrayList();
      for (int i = 0; i < levelSize; i++) {
        //遍历当前层级的节点，添加值到数组里并将子节点添加到队列中
        TreeNode node = queue.poll();
        listLevel.add(node.val);

        //左右子节点不空时才添加
        if (node.left != null) {
          queue.offer(node.left);
        }
        if (node.right != null) {
          queue.offer(node.right);
        }
      }
      listResult.add(listLevel);
    }

    return listResult;
  }
}

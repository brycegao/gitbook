#HashMap源码分析（JDK1.8）

> 摘要：HashMap是每个Android和Java后台程序员经常用到的类， 本文阐述了HashMap基本原理和若干个实现细节， 帮助读者更深刻的理解和使用它。

HashMap是Java和Android程序员的基本功， JDK1.8对HashMap进行了优化， 你真正理解它了吗？ 

考虑如下问题：  

1、哈希基本原理？（答：散列表、hash碰撞、链表、红黑树）

2、hashmap查询的时间复杂度， 影响因素和原理？ （答：最好O（1），最差O（n）， 如果是红黑O（logn））
3、resize如何实现的， 记住已经没有rehash了！！！（答：拉链entry根据高位bit散列到当前位置i和size+i位置）

4、为什么获取下标时用按位与&，而不是取模%？ （答：不只是&速度更快哦，  我觉得你能答上来便真正理解hashmap了）

5、什么时机执行resize？

答：hashmap实例里的元素个数大于threshold时执行resize(即桶数量扩容为2倍并散列原来的Entry)。 PS：threshold=桶数量*负载因子
```
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
               boolean evict) {
    Node<K,V>[] tab; Node<K,V> p; int n, i;
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;   //初始化桶，默认16个元素
    if ((p = tab[i = (n - 1) & hash]) == null)   //如果第i个桶为空，创建Node实例
        tab[i] = newNode(hash, key, value, null);
    else { //哈希碰撞的情况， 即(n-1)&hash相等
        Node<K,V> e; K k;
        if (p.hash == hash &&
            ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;   //key相同，后面会覆盖value
        else if (p instanceof TreeNode)
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);  //红黑树添加当前node
        else {
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {
                    p.next = newNode(hash, key, value, null);  //链表添加当前元素
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash);  //当链表个数大于等于7时，将链表改造为红黑树
                    break;
                }
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                p = e;
            }
        }
        if (e != null) { // existing mapping for key
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);
            return oldValue;            //覆盖key相同的value并return， 即不会执行++size
        }
    }
    ++modCount;
    if (++size > threshold)    //key不相同时，每次插入一条数据自增1. 当size大于threshold时resize
        resize();
    afterNodeInsertion(evict);
    return null;
 }
```

6、为什么负载因子默认为0.75f ？ 能不能变为0.1、0.9、2、3等等呢？

答：0.75是平衡了时间和空间等因素； 负载因子越小桶的数量越多，读写的时间复杂度越低（极限情况O(1), 哈希碰撞的可能性越小）； 负载因子越大桶的数量越少，读写的时间复杂度越高(极限情况O(n), 哈希碰撞可能性越高)。 0.1，0.9，2，3等都是合法值。

7、影响HashMap性能的因素？

(1) 负载因子；
(2) 哈希值；理想情况是均匀的散列到各个桶。 一般HashMap使用String类型作为key，而String类重写了hashCode函数。
```
static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

8、HashMap的key需要满足什么条件？ 
答：必须重写hashCode和equals方法， 常用的String类实现了这两个方法。

9、HashMap允许key/value为null， 但最多只有一个。 为什么？  
答： 如果key为null会放在第一个桶（即下标0）位置， 而且是在链表最前面（即第一个位置）。 
JDK1.8的HashMap源码：[http://www.grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/8u40-b25/java/util/HashMap.java#HashMap](http://www.grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/8u40-b25/java/util/HashMap.java#HashMap)

我的习惯是先看注释再看源码并调试， 先翻译一下源码注释吧， 不准之处请指正哈。

Hash table based implementation of the Map interface. This implementation provides all of the optional map

**HashTable实现了Map接口类， 这些接口实现了所有可选的map功能， 包括允许空值和空key。**

operations, and permits null values and thenull key. (TheHashMap class is roughly equivalent toHashtable, except that it is unsynchronized and permits nulls.) This class makes no guarantees as to the order of the map; in particular, it does not guarantee that the order will remain constant over time.

**HashMap和HashTable基本一致，  区别是HashMap是线程不同步的且允许空key。 HashMap不保证map的顺序， 而且顺序是可变的。**

This implementation provides constant-time performance for the basic operations (get andput), assuming the hash function disperses the elements properly among the buckets.

**如果将数据适当的分散到桶里， HashMap的添加、查询函数的执行周期是常量值。**

Iteration over collection views requires time proportional to the "capacity" of theHashMap instance (the number of buckets) plus its size (the number of key-value mappings). Thus, it's very important not to set the initial capacity too high (or the load factor too low) if iteration performance is important.

**使用迭代器遍历所有数据的性能跟HashMap的桶（bucket）数量有直接关系，   为了提高遍历的性能， 不能设置比较大的桶数量或者负载因子过低。**

An instance of HashMap has two parameters that affect its performance:initial capacity andload factor. Thecapacity is the number of buckets in the hash table, and the initial capacity is simply the capacity at the time the hash table is created.

**HashMap实例有2个重要参数影响它的性能： 初始容量和负载因子。 初始容量是指在哈希表里的桶总数， 一般在创建HashMap实例时设置初始容量。**

The load factor is a measure of how full the hash table is allowed to get before its capacity is automatically increased.

**负载因子是指哈希表在多满时扩容的百分比比例。**

When the number of entries in the hash table exceeds the product of the load factor and the current capacity, the hash table isrehashed (that is, internal data structures are rebuilt) so that the hash table has approximately twice the number of buckets.

**当哈希表的数据个数超过负载因子和当前容量的乘积时， 哈希表要再做一次哈希（重建内部数据结构）， 哈希表每次扩容为原来的2倍。**

As a general rule, the default load factor (.75) offers a good tradeoff between time and space costs. Higher values decrease the space overhead but increase the lookup cost (reflected in most of the operations of theHashMap class, including get and put). 

**负载因子的默认值是0.75， 它平衡了时间和空间复杂度。 负载因子越大会降低空间使用率，但提高了查询性能（表现在哈希表的大多数操作是读取和查询）**

The expected number of entries in the map and its load factor should be taken into account when setting its initial capacity, so as to minimize the number of rehash operations. If the initial capacity is greater than the maximum number of entries divided by the load factor, no rehash operations will ever occur.

**考虑哈希表的性能问题， 要设置合适的初始容量，从而减少rehash的次数。 当哈希表中entry的总数少于负载因子和初始容量乘积时，就不会发生rehash动作。**

If many mappings are to be stored in a HashMap instance, creating it with a sufficiently large capacity will allow the mappings to be stored more efficiently than letting it perform automatic rehashing as needed to grow the table. Note that using many keys with the same hashCode() is a sure way to slow down performance of any hash table. To ameliorate impact, when keys arejava.lang.Comparable, this class may use comparison order among keys to help break ties. 

**如果有很多值要存储到HashMap实例中， 在创建HashMap实例时要设置足够大的初始容量， 避免自动扩容时rehash。 如果很多关键字的哈希值相同， 会降低哈希表的性能。 为了降低这个影响， 当关键字支持java.lang.Comparable时， 可以对关键字做次排序以降低影响。**

Note that this implementation is not synchronized. If multiple threads access a hash map concurrently, and at

least one of the threads modifies the map structurally, itmust be synchronized externally. (A structural modification

**哈希表是非线程安全的， 如果多线程同时访问哈希表， 且至少一个线程修改了哈希表的结构， 那么必须在访问hashmap前设置同步锁。（修改结构是指添加或者删除一个或多个entry， 修改键值不算是修改结构。）**

is any operation that adds or deletes one or more mappings; merely changing the value associated with a key that an instance already contains is not a structural modification.) This is typically accomplished by synchronizing on some object that naturally encapsulates the map. 

**一般在多线程操作哈希表时，  要使用同步对象封装map。**

If no such object exists, the map should be "wrapped" using theCollections.synchronizedMap method. This is best done at creation time, to prevent accidental unsynchronized access to the map:

**如果不封装Hashmap， 可以使用Collections.synchronizedMap  方法调用HashMap实例。  在创建HashMap实例时避免其他线程操作该实例， 即保证了线程安全。**

Map m = Collections.synchronizedMap(new HashMap(...));

**JDK1.8对哈希碰撞后的拉链算法进行了优化， 当拉链上entry数量太多（超过8个）时，将链表重构为红黑树。  下面是源码相关的注释：**

>  * This map usually acts as a binned (bucketed) hash table, but
      * when bins get too large, they are transformed into bins of
      * TreeNodes, each structured similarly to those in
      * java.util.TreeMap. Most methods try to use normal bins, but
      * relay to TreeNode methods when applicable (simply by checking
      * instanceof a node).  Bins of TreeNodes may be traversed and
      * used like any others, but additionally support faster lookup
      * when overpopulated. However, since the vast majority of bins in
      * normal use are not overpopulated, checking for existence of
      * tree bins may be delayed in the course of table methods.

看看HashMap的几个重要成员变量：

 //The default initial capacity - MUST be a power of two.

static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; //为什么不写成16？？？ 大师是想用这种写法告诉你只能是2的幂

**HashMap的初始容量是16个， 而且容量只能是2的幂。  每次扩容时都是变成原来的2倍。**

static final float DEFAULT_LOAD_FACTOR = 0.75f;

默认的负载因子是0.75f， 16*0.75=12。即默认的HashMap实例在插入第13个数据时，会扩容为32。

The bin count threshold for using a tree rather than list for a bin. Bins are converted to trees when adding an element to a bin with at least this many nodes. The value must be greater than 2 and should be at least 8 to mesh with assumptions in tree removal about conversion back to plain bins upon shrinkage.
static final int TREEIFY_THRESHOLD = 8;

**注意：这是JDK1.8对HashMap的优化， 哈希碰撞后的链表上达到8个节点时要将链表重构为红黑树，  查询的时间复杂度变为O(logN)。**

The table, initialized on first use, and resized as necessary. When allocated, length is always a power of two. (We also tolerate length zero in some operations to allow bootstrapping mechanics that are currently not needed.) 
transient Node<K,V>[] table;  //HashMap的桶， 如果没有哈希碰撞， HashMap就是个数组，我说的是如果吐舌头。  数组的查询时间复杂度是O(1)，所以HashMap理想时间复杂度是O(1)；如果所有数据都在同一个下标位置， 即N个数据组成链表，时间复杂度为O(n)， 所以HashMap的最差时间复杂度为O(n)。如果链表达到8个元素时重构为红黑树，而红黑树的查询时间复杂度为O(logN), 所以这时HashMap的时间复杂度为O(logN)。

Holds cached entrySet(). Note that AbstractMap fields are used for keySet() and values().
transient Set<Map.Entry<K,V>> entrySet; //HashMap所有的值，因为用了Set， 所以HashMap不会有key、value都相同的数据。
      
1、 哈希碰撞的原因和解决方法：
哈希碰撞是不同的key值找到相同的下标，  对应HashMap里hashcode和容量的模相同。

源码629行    <big>if ((p = tab[i = (n - 1) & hash]) == null)</big>， 其中n是容量值，    即用哈希值和容量相与得到要保存的位置。 如果不同Key的(n - 1) & hash相同， 那么要存储到同一个数组下标位置， 这个现象就叫哈希碰撞。
```
final V putVal(int hash, K key, V value, boolean onlyIfAbsent,boolean evict) {
      if ((p = tab[i = (n - 1) & hash]) == null)     //如果该下标没值，则存储到该下标位置
             tab[i] = newNode(hash, key, value, null);      
         else {
             Node<K,V> e; K k;
             if (p.hash == hash &&
                 ((k = p.key) == key || (key != null && key.equals(k))))
                 e = p;      //如果哈希值相同而且key相同， 则更新键值
             else if (p instanceof TreeNode)
                 e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);  //如果下标数据是TreeNode类型，则将新数据添加到红黑树中。
             else {
                 for (int binCount = 0; ; ++binCount) {
                     if ((e = p.next) == null) {
                         p.next = newNode(hash, key, value, null);   //将新Node添加到链表末尾
                         if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                             treeifyBin(tab, hash);    //如果链表个数达到8个时，将链表修改为红黑树结构
                         break;
                     }     
           //省略若干行代码          
 }
```

2、JDK1.8对HashMap最大的优化是resize函数，  在扩容时不再需要rehash了， 下面就看看大师是怎么实现的。

Initializes or doubles table size. If null, allocates in accord with initial capacity target held in field threshold. Otherwise, because we are using power-of-two expansion, the elements from each bin must either stay at same index, or move with a power of two offset in the new table.

初始化数组或者扩容为2倍，   初值为空时，则根据初始容量开辟空间来创建数组。否则， 因为我们使用2的幂定义数组大小，数据要么待在原来的下标， 或者移动到新数组的高位下标。 （举例： 初始数组是16个，假如有2个数据存储在下标为1的位置， 扩容后这2个数据可以存在下标为1或者16+1的位置）
```
final Node<K,V>[] resize() {
   newThr = oldThr << 1; // double threshold,   大小扩大为2倍，出于性能考虑和者告诉使用者它是2的幂， 这里用的是位移， 而不是*2，
   if (e.next == null)
      newTab[e.hash & (newCap - 1)] = e;  //如果该下标只有一个数据，则散列到当前位置或者高位对应位置（以第一次resize为例，原来在第4个位置，resize后会存储到第4个或者第4+16个位置）
  else if (e instanceof TreeNode)
     ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);  //红黑树重构
  else {
     do {
        next = e.next;
        if ((e.hash & oldCap) == 0) {    
            if (loTail == null)
               loHead = e;
            else
            loTail.next = e;
            loTail = e;
        } else {
            if (hiTail == null)
               hiHead = e;
            else
               hiTail.next = e;
               hiTail = e;
         }
      } while ((e = next) != null);
      if (loTail != null) {
          loTail.next = null;
          newTab[j] = loHead;   //下标不变
      }
      if (hiTail != null) {
          hiTail.next = null;
          newTab[j + oldCap] = hiHead; //下标位置移动原来容量大小
      }
```

 **(e.hash & oldCap) == 0**写的很赞！！！ 它将原来的链表数据散列到2个下标位置，  概率是当前位置50%，高位位置50%。     你可能有点懵比， 下面举例说明。  上边图中第0个下标有496和896，  假设它俩的hashcode(int型，占4个字节)是
 
 resize前：

496的hashcode: 00000000  00000000  00000000  00000000

896的hashcode: 01010000  01100000  10000000  00100000

oldCap是16: ###00000000  00000000  00000000  00010000

496和896对应的**e.hash & oldCap**的值为0， 即下标都是第0个。



resize后：

496的hashcode: 00000000  00000000  00000000  00000000

896的hashcode: 01010000  01100000  10000000  00100000

oldCap是32: ###00000000  00000000  00000000  00100000
   
496和896对应的****的值为0和1， 即下标都是第0个和第16个。

看明白了吗？   因为hashcode的第n位是0/1的概率相同， 理论上链表的数据会均匀分布到当前下标或高位数组对应下标。

   回顾JDK1.7的HashMap，在扩容时会rehash即每个entry的位置都要再计算一遍，  性能不好呀， 所以JDK1.8做了这个优化。

  再回到文章最开始的问题， HashMap为什么用&得到下标，而不是%？ 如果使用了取模%， 那么在容量变为2倍时， 需要rehash确定每个链表元素的位置。

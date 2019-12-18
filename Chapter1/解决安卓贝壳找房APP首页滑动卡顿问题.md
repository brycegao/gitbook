# 解决安卓贝壳找房APP首页滑动卡顿问题


> 摘要：描述分析、解决贝壳找房app首页滑动卡顿问题的过程。

背景： 打开贝壳找房app后上下滑动界面， 明显感觉到顿挫感， 即使在安卓高端机(内存6G)也如此， 说明不是硬件配置低的锅。

思路： 造成UI卡顿分为3方面原因；
>1. CPU太忙， 即手机运行了很多app和服务， 占用了大量的CPU； CPU是负责数据运算的， 通过SurfaceFlinger告诉GPU要显示什么。
>2. GPU太忙， GPU接收CPU传来的指令， 如果指令太多可能会导致丢帧/卡顿， 渲染一帧图像要在16ms以内才能保证UI的流畅性；
>3. 内存不足， 手机运行太多的app和服务，没有充裕内存空间分配到当前app，但现在市场安卓手机内存主流水平是3G/4G/6G/8G，一般可以忽略该因素； 但当前app占用的内存空间太大可能导致卡顿。 通常是app出现内存泄漏或者占用内存太高(例如缓存了图片、文件或其它数据结构)。

PS： 分析root cause占用八成时间， 解决问题占用两成时间； 
  
   从哪个点入手分析问题？？？
     
   我的建议是从简单到复杂， 即先分析GPU性能(专业叫法是过度绘制，即一个像素被绘制了多次)。
   
   打开 系统设置 --- 开发者选择 --- 调试GPU过度绘制 --- 显示过度绘制区域  和 系统设置 --- 开发者选择 --- GPU呈现模式分析 --- 在屏幕上显示为条形图， 然后再看看要调试的界面；
   
<center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/1.png" width="50%" height="50%" />
</center>
<center>贝壳找房首页</center>

<center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/2.png" width="50%" height="50%" />
</center>
<center>过度渲染示意图</center>

通过安卓自带功能可以看出首页过度绘制问题很严重， 底部柱状图显示每帧图像渲染时间都超过16ms， 意味着丢帧/卡段现象比较严重。

那么怎么解决过度绘制问题呢？ 原理：从叶节点View/ViewGroup向上找出所有的父/祖父/曾祖父的ViewGroup， 一直到布局根节点，删除不必要的背景（**设置背景就要多渲染一次**）；合理调整UI布局，尽量减少层级。 
>1. 删除贝壳找房首页Activity的主题背景， 可以在xml样式里设置或在onCreate里赋空;
>2. 确认Activity布局根节点ViewGroup是否设置背景色， 如果有的话要删除，让子View/ViewGroup绘制背景； （减少一次绘制）
>3. 判断Fragment布局根节点ViewGroup是否设置背景色，如果有的话要删除；（减少一次绘制）       
>4. 首页每个布局卡片是否设置了背景；
>5. 像上图显示的推荐房源ListView是否设置了背景；
>6. 代码里是否有不必要的notifyDataSetChanged，invalidate，requestLayout等行为；这都会导致对应View/ViewGroup重新绘制；   
>7. 背景重叠可以通过重写onDraw函数， 在缓存canvas里绘制完后一次性刷新到界面；

<center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/3.png" width="50%" height="50%" />
</center>
<center>优化后首页</center>

按照如上方式优化后效果很明显， 柱状图基本都在16ms以下， 基本满足性能需要，但还是有优化的空间。
  
 这时上下快滑首页还是能感觉到UI卡顿， 这时就要分析CPU性能了， 即在主线程里是否做了耗时操作； 这时我们可以借助TraceView分析每个函数的执行周期； 打开sdk的monitor。
 
 <center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/4.png" width="50%" height="50%" />
</center>
  选中要调试的进程， 点击红圈内按钮开始， 再次点击结束； 按照降序排列所有记录。 Parent表示当前函数被调用的地方， Children表示当前函数调用的其它函数； 怀疑MyScrollView的handleMessage在搞事情。
   <center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/5.png" width="50%" height="50%" />
</center>
点击“com.homelink.midlib.view.MyScrollView$1.handleMessage"这一行， 可以看到handleMessage函数的执行情况； 可以看到onScroll函数执行了358毫秒，而且是在主线程， UI不卡顿等啥呢。
   <center> 
<img src="https://raw.githubusercontent.com/brycegao/open-resource/master/6.png" width="50%" height="50%" />
</center>

  看看MyScrollView的Handler都做了什么， 就是sendMessageDelayed给自己发送个延时消息， 然后就是调用回调了。  继续看看这个onScroll都做了什么。
  <pre>override fun onSrcoll(scrollY: Int) {
    LjExposureUtil.statistics(mExposureCardList, mVisibleList)
    mRecommendHouseCard?.childExposure()
  }</pre>
  跟进代码看到onScroll就是触发了埋点， 但它是个耗时操作，**在滑动过程中会多次执行onScroll，导致卡顿的问题**。
  
  发现问题了， 解决就很简单了。 我们希望的是如丝般顺滑的滑动体验， 那就不要在滑动过程中阻塞主线程， 我们可以在滑动结束时做埋点操作；
  
<pre> override fun onScrollStop() {
   LjExposureUtil.statistics(mExposureCardList, mVisibleList)
   mRecommendHouseCard?.childExposure()
}
</pre>
  
  后续工作， 使用RecyclerView改造首页。